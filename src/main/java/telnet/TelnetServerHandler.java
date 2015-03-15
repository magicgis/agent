package telnet;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.net.InetAddress;

import processor.CommandProcessor;

@Sharable
public class TelnetServerHandler extends SimpleChannelInboundHandler<String> {

	private CommandProcessor commandProcessor;

	public TelnetServerHandler(CommandProcessor commandProcessor) {
		this.commandProcessor = commandProcessor;
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		// Send greeting for a new connection.
		ctx.write("Agent on " + InetAddress.getLocalHost().getHostName() + "\r\n");
		ctx.flush();
	}

	@Override
	public void channelRead0(ChannelHandlerContext ctx, String request) throws Exception {
		// Generate and write a response.
		String response;
		boolean close = false;
		if (request.isEmpty()) {
			response = "\r\n";
		} else if ("exit".equals(request.toLowerCase())) {
			response = "Bye!\r\n";
			close = true;
		} else {
			response = commandProcessor.process(request) + "\r\n";
		}

		// We do not need to write a ChannelBuffer here.
		// We know the encoder inserted at TelnetPipelineFactory will do the
		// conversion.
		ChannelFuture future = ctx.write(response);

		// Close the connection after sending 'Have a good day!'
		// if the client has sent 'bye'.
		if (close) {
			future.addListener(ChannelFutureListener.CLOSE);
		}
	}

	@Override
	public void channelReadComplete(ChannelHandlerContext ctx) {
		ctx.flush();
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		cause.printStackTrace();
		ctx.close();
	}
}
