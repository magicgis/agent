package processor;

import java.util.HashMap;
import java.util.Map;

import job.JobDetail;
import job.JobManager;
import upload.UploadJob;
import utils.StringUtils;
import agent.Agent;

public class UploadCommand extends CommandBase implements Command {

	private static final String NO_PROXY = "noproxy";

	@Override
	public String execute(String request) {
		String[] params = parameters(request);
		if (params.length < 2 || StringUtils.nullOrEmpty(params[0]) || StringUtils.nullOrEmpty(params[1])) {
			return syntaxError();
		}

		JobManager jobManager = Agent.get().getJobManager();

		String location = params[0];
		String url = params[1];
		boolean noProxy = (params.length == 3 && NO_PROXY.equals(params[2]));

		Map<String, String> parameters = new HashMap<String, String>();
		parameters.put(UploadJob.PARAM_URL, url);
		parameters.put(UploadJob.PARAM_LOCATION, location);
		parameters.put(UploadJob.PARAM_NOPROXY, String.valueOf(noProxy));

		JobDetail jobDetail = new JobDetail(UploadJob.class, parameters);
		jobManager.submit(jobDetail);

		return jobDetail.toString();

	}

	@Override
	public String help() {
		
		return "upload\t<location> <agent-upload-url> [noproxy]\t\tupload file from <location> to <agent-upload-url> using proxy" + StringUtils.CRLF
				+ StringUtils.CRLF + 
				"Examples:" + StringUtils.CRLF + 
				"upload\tbundle.zip http://agent1:21080/upload\t\tuse proxy if available" + StringUtils.CRLF + 
				"upload\tbundle.zip http://agent1:21080/upload noproxy\tignore proxy configuration";

	}

	@Override
	public String name() {
		return "upload";
	}

}
