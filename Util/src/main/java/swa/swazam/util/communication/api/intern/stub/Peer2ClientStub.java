package swa.swazam.util.communication.api.intern.stub;

import java.net.InetSocketAddress;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;

import swa.swazam.util.communication.Peer2Client;
import swa.swazam.util.communication.api.Startable;
import swa.swazam.util.communication.api.intern.ClientSide;
import swa.swazam.util.communication.api.intern.dto.NetPacketFactory;
import swa.swazam.util.communication.api.intern.dto.RequestWirePacket;
import swa.swazam.util.dto.MessageDTO;
import swa.swazam.util.exceptions.SwazamException;

public class Peer2ClientStub implements Peer2Client, Startable {

	private final ClientSide clientSide;
	private InetSocketAddress localListenAddress = null; // has to be initialized after comm startup (but before actual communication)

	public Peer2ClientStub(ClientSide clientSide) {
		this.clientSide = clientSide;
	}

	@Override
	public void startup() throws SwazamException {}

	@Override
	public void shutdown() {}

	public void updateLocalListenAddress(InetSocketAddress localListenAddress) {
		this.localListenAddress = localListenAddress;
	}

	@Override
	public void solved(MessageDTO answer, InetSocketAddress client) {
		ChannelFuture connectFuture = clientSide.connect(client);
		Channel channel = connectFuture.awaitUninterruptibly().getChannel();
		if (!connectFuture.isSuccess()) {
			return; // client nicht erreichbar
		}

		answer.setResolverAddress(localListenAddress);

		RequestWirePacket packet = NetPacketFactory.createRequestWirePacket("solved", answer);
		clientSide.callRemoteMethodNoneBlocking(channel, packet).awaitUninterruptibly();

		channel.close();
	}
}
