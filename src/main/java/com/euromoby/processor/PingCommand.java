package com.euromoby.processor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.euromoby.ping.PingSender;
import com.euromoby.ping.model.PingInfo;
import com.euromoby.ping.model.PingStatus;
import com.euromoby.utils.StringUtils;

@Component
public class PingCommand extends CommandBase implements Command {

	public static final String NAME = "ping";
	public static final String NO_PROXY = "noproxy"; 
	public static final String RESPONSE_TIME = "Response time:";
	
	private PingSender pingSender;
	
	@Autowired
	public PingCommand(PingSender pingSender) {
		this.pingSender = pingSender;
	}
	
	@Override
	public String execute(String request) {
		String[] params = parameters(request);
		if (params.length < 2 || StringUtils.nullOrEmpty(params[0]) || StringUtils.nullOrEmpty(params[1])) {
			return syntaxError();
		}

		String host = params[0];
		String restPort = params[1];
		boolean noProxy = (params.length == 3 && NO_PROXY.equals(params[2]));

		PingStatus pingStatus = new PingStatus();
		long start = System.currentTimeMillis();
		try {
			PingInfo pingInfo = pingSender.ping(host, Integer.parseInt(restPort), noProxy);
			return pingInfo.getAgentId().toString() + StringUtils.CRLF + pingStatus.toString() + StringUtils.CRLF + RESPONSE_TIME + (pingStatus.getTime() - start);
		} catch (Exception e) {
			pingStatus.setError(true);
			pingStatus.setMessage(e.getMessage());
		} finally {
			pingStatus.setTime(System.currentTimeMillis());
		}
		
		return host+":"+ restPort + StringUtils.CRLF + pingStatus.toString() + StringUtils.CRLF + RESPONSE_TIME + (pingStatus.getTime() - start);
		
	}

	@Override
	public String help() {
		return NAME + "\t<agent-host> <agent-rest-port> [noproxy]\tsend ping to <agent-host>:<agent-rest-port> (using configured proxy or directly)" + StringUtils.CRLF
				+ StringUtils.CRLF + 
				"Examples:" + StringUtils.CRLF + 
				NAME + "\tagent1 21443\t\tuse proxy if available" + StringUtils.CRLF + 
				NAME + "\tagent1 21443 noproxy\tignore proxy configuration";

	}	

	@Override
	public String name() {
		return NAME;
	}

}
