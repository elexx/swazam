package swa.swazam.client;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Timer;
import java.util.UUID;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import swa.swazam.util.communication.Client2Server;
import swa.swazam.util.communication.ClientCallback;
import swa.swazam.util.communication.General2Peer;
import swa.swazam.util.communication.api.ClientCommunicationUtil;
import swa.swazam.util.dto.CredentialsDTO;
import swa.swazam.util.dto.MessageDTO;
import swa.swazam.util.dto.RequestDTO;
import swa.swazam.util.exceptions.SwazamException;
import swa.swazam.util.fingerprint.FingerprintTools;
import swa.swazam.util.hash.HashGenerator;
import swa.swazam.util.peerlist.ArrayPeerList;
import swa.swazam.util.peerlist.PeerList;
import ac.at.tuwien.infosys.swa.audio.Fingerprint;

/**
 * App represents the client application of swazam. The app manages a peer list, can take a music recording, fingerprint it, and send it to peers to identify it.
 */
public class App {

	private static final String TESTDATA = "chrissi";
	private static final String TESTFILE = TESTDATA + ".mp3";

	// private final RequestManager requestManager;

	private ClientCommunicationUtil commLayer;
	private Client2Server server;
	private General2Peer peers;

	private CredentialsDTO user;
	private RequestDTO request;
	private MessageDTO message;

	private PeerList<InetSocketAddress> peerList;

	private ClientCallback callback; // maybe not needed
	private InetSocketAddress client;
	private BufferedReader br;

	public App() {
		peerList = new ArrayPeerList<>();
		br = new BufferedReader(new InputStreamReader(System.in));

		// requestManager = new RequestManager();
		// requestManager.setup(this);
	}

	/**
	 * checks users coin status on server
	 * 
	 * @return true if user has at least one coin left, false otherwise
	 * @throws SwazamException if server not reachable for example
	 */
	private boolean checkforCoins() throws SwazamException {

		return server.hasCoins(user);
	}

	/**
	 * Login at server with username and password
	 * 
	 * @param username
	 * @param password (not hashed yet)
	 * @return true when user is registered at server and username plus hashed password combination is correct false otherwise
	 * @throws SwazamException if server not reachable for example
	 */
	private boolean login(String username, String password) throws SwazamException {
		user = new CredentialsDTO(username, HashGenerator.hash(password));

		return server.verifyCredentials(user);

	}

	private void createRequest(Fingerprint fingerprint) {
		request = new RequestDTO(UUID.randomUUID(), client, fingerprint);
		createMessage(request);
	}

	private void createMessage(RequestDTO request) {
		message = new MessageDTO(request.getUuid(), "", "", null);
	}

	private static void welcomeMessage() {
		System.out.println("Welcome to SWAzam!\n");
		System.out.println("To try out our service, we registered already a testuser with a number of coins");
		System.out.println("Login and password are: chrissi\n");
		System.out.println("A test recording, containing a 7 second random song snippet we provide as well: chrissi.mp3");
		System.out.println("Have fun with SWAzam!");
	}

	/**
	 * initially getPeerList or if #Peers in PeerList < 5
	 * 
	 * @throws SwazamException
	 */
	private void updatePeerlist() throws SwazamException {
		if (peerList.size() < 5) {
			PeerList<InetSocketAddress> oldPeers = new ArrayPeerList<>();
			oldPeers.addAll(peerList);
			peerList.clear(); // to avoid duplicates
			peerList.addAll(server.getPeerList()); // add new peers
			for (InetSocketAddress inetSocketAddress : oldPeers) {
				if (!peerList.contains(inetSocketAddress)) {
					peerList.add(0, inetSocketAddress); // add old, presumably good peers, to top of list
				}
			}
		}
	}

	public void run() {
		// read configuration (server address, client port for peers)
		loadConfig();

		// let user enter username and password on commandline
		try {
			performLogin();

			searchForSnippet();

		} catch (SwazamException e) {
			// TODO Auto-generated catch block
			System.err.println("Server, internet connection, or database are down. Please try again later.");
			e.printStackTrace();
			System.exit(0);
		}

	}

	private void searchForSnippet() throws SwazamException {
		boolean hasCoins = false;
		Fingerprint fingerprint = null;
		boolean tryAgain = true;

		hasCoins = checkforCoins();

		if (!hasCoins) {
			System.out.println("No more coins available. You receive coins when your peer program identifies a song request of other clients.");
			System.err.println("Client quitting because of no coins");
			return;
		}

		fingerprint = generateFingerprintForSnippet();

		while (tryAgain) {
			createRequest(fingerprint); // create UUID for RequestDTO, create MessageDTO with UUID filled out already
			server.logRequest(user, message); // logRequest sends UUID/MessageDTO to Server
			updatePeerlist();

			// send MessageDTO (with UUID fingerprint) to top 5 peers from client peerlist in parallel
			// TODO eventually also receive a return value of a successful or failed connection attempt to first peers, so that more peers in list can be tried without the request failing after first 5 not online
			peers.process(request, peerList.getTop(5));

			// wait 30 seconds, if no answer, display to user, "song snippet not found, try again later?/discard"
			System.out.print("searching");
			for (int i = 0; i <= 10; i++) {
				System.out.print(".." + (i * 10) + "%");
				try {
					callback.wait(3000);
				} catch (InterruptedException e) {
					// TODO song found, add message data, log at server
					// update peerlist.return result to display.

					return;
				}
			}
			// TODO peer list management, remove top5 from top;
			tryAgain = false;
			System.out.println(". Song snippet not found this time.");

			hasCoins = checkforCoins();

			String answer = "d";

			if (hasCoins) {
				System.out.println("Try again (a)? or Discard (d) and start new search? [a|(d)]:"); // search again with different top5 or discard and loop back to hascoins check

				try {
					answer = br.readLine();
					if (answer.equalsIgnoreCase("a")) {
						tryAgain = true;
					}
				} catch (IOException e) {
					System.err.println("Could not read input, opting for default answer: Discard");
				}
			}
		}
	}

	private Fingerprint generateFingerprintForSnippet() throws SwazamException {
		String snippet;
		Fingerprint fingerprint = null;
		do {
			try {
				// (record or) select music snippet file
				System.out.print("song snippet filename: ");
				snippet = br.readLine();
			} catch (IOException e) {
				System.err.println("Song snippet cannot be read. Standard filename '" + TESTDATA + ".mp3' is used."); // exit alternatively
				snippet = TESTFILE;
			}

			File snippetFile = new File(snippet);

			try {
				fingerprint = new FingerprintTools().generate(AudioSystem.getAudioInputStream(snippetFile));
			} catch (UnsupportedAudioFileException | IOException e) {
				System.err.println("Audio format not supported or file could not be read.");
			}
		} while (fingerprint == null);

		return fingerprint;
	}

	/**
	 * user ist logged in or an exception is thrown.
	 * 
	 * @return always true or exception
	 * @throws SwazamException
	 */
	private boolean performLogin() throws SwazamException {
		boolean loginSuccessful = false;
		int loginAttempt = 0;

		do {
			String username = getUsernameFromUser();
			if (loginAttempt != 0) {
				System.out.println("please wait for " + (loginAttempt) * 500 + "ms to enter password."); // render brute force unfeasable, avoid Server login DOS with user credentials except of default
			}
			try {
				Thread.sleep(loginAttempt * 500);
			} catch (InterruptedException e) {
				System.err.println("Maybe another client instance is still running?");
			} finally {
				loginAttempt++;
			}

			String password = getPasswortFromUser();

			loginSuccessful = login(username, password);

		} while (!loginSuccessful);

		return loginSuccessful;
	}

	private String getPasswortFromUser() {
		String password;
		try {
			System.out.print("password: ");
			password = br.readLine();
		} catch (IOException e1) {
			System.err.println("Password cannot be read. Falling back to standard password '" + TESTDATA + "'."); // exit alternatively
			password = TESTDATA;
		}
		return password;
	}

	private String getUsernameFromUser() {
		String username;
		try {
			System.out.print("username: ");
			username = br.readLine();
		} catch (IOException e1) {
			System.err.println("Username cannot be read. Falling back to standard username '" + TESTDATA + "'."); // exit alternatively
			username = TESTDATA;
		}
		return username;
	}

	private void loadConfig() {
		// TODO Auto-generated method stub

	}

	public static void main(String[] args) {
		App app = new App();
		welcomeMessage();
		app.run();

	}

}
