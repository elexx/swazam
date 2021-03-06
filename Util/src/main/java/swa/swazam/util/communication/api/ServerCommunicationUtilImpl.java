package swa.swazam.util.communication.api;

import java.net.InetSocketAddress;

import swa.swazam.util.communication.ServerCallback;
import swa.swazam.util.communication.api.intern.ServerSide;
import swa.swazam.util.exceptions.SwazamException;

class ServerCommunicationUtilImpl implements ServerCommunicationUtil {
	private final ServerSide serverSide;

	ServerCommunicationUtilImpl(InetSocketAddress listenAddress) {
		serverSide = new ServerSide(listenAddress);
	}

	@Override
	public void startup() throws SwazamException {
		serverSide.startup();
	}

	@Override
	public void shutdown() {
		serverSide.shutdown();
	}

	@Override
	public void setCallback(ServerCallback callback) {
		serverSide.setCallback(callback);
	}
}
