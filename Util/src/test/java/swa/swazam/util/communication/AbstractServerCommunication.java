package swa.swazam.util.communication;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.junit.After;
import org.junit.Before;

import swa.swazam.util.communication.api.CommunicationUtilFactory;
import swa.swazam.util.communication.api.ServerCommunicationUtil;
import swa.swazam.util.dto.CredentialsDTO;

public abstract class AbstractServerCommunication {

	protected List<InetSocketAddress> peerList;
	protected CredentialsDTO credentialsWithCoins;
	protected CredentialsDTO credentialsWithoutCoins;
	private ServerCommunicationUtil serverCommUtil;

	@Before
	public final void startup() throws Exception {
		peerList = new LinkedList<>();
		peerList.add(new InetSocketAddress("localhost", 1234));
		peerList.add(new InetSocketAddress("127.0.0.1", 4567));
		peerList = Collections.unmodifiableList(peerList);
		credentialsWithCoins = new CredentialsDTO("testuser", "apassword");
		credentialsWithoutCoins = new CredentialsDTO("pooruser", "anotherpassword");

		serverCommUtil = CommunicationUtilFactory.createServerCommunicationUtil();
		serverCommUtil.startup();
		serverCommUtil.setCallback(mockServer());
	}

	@After
	public final void shutdown() {
		serverCommUtil.shutdown();
	}

	private ServerCallback mockServer() throws Exception {
		ServerCallback callback = mock(ServerCallback.class);
		when(callback.getPeerList()).thenReturn(peerList);
		when(callback.verifyCredentials(credentialsWithCoins)).thenReturn(true);
		when(callback.hasCoins(credentialsWithCoins)).thenReturn(true);
		when(callback.hasCoins(credentialsWithoutCoins)).thenReturn(false);
		return callback;
	}
}
