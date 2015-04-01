package com.euromoby.job;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.euromoby.download.DownloadClient;
import com.euromoby.download.DownloadJob;
import com.euromoby.upload.UploadClient;
import com.euromoby.upload.UploadJob;

@Component
public class JobFactory {
	
	private UploadClient uploadClient;
	private DownloadClient downloadClient;
	private GetJobsClient getJobsClient;
	
	@SuppressWarnings("rawtypes")
	private Class[] jobClasses = new Class[]{
			DownloadJob.class, UploadJob.class, GetNewJobsJob.class
	};
	
	@SuppressWarnings("rawtypes")
	public Class[] getJobClasses() {
		return jobClasses;
	}
	
	@Autowired
	public void setUploadClient(UploadClient uploadClient) {
		this.uploadClient = uploadClient;
	}

	@Autowired
	public void setDownloadClient(DownloadClient downloadClient) {
		this.downloadClient = downloadClient;
	}

	@Autowired	
	public void setGetJobsClient(GetJobsClient getJobsClient) {
		this.getJobsClient = getJobsClient;
	}

	public Job createJob(JobDetail jobDetail) throws Exception {
		if (DownloadJob.class.getCanonicalName().equals(jobDetail.getJobClass())) {
			return new DownloadJob(jobDetail, downloadClient);
		}
		if (UploadJob.class.getCanonicalName().equals(jobDetail.getJobClass())) {
			return new UploadJob(jobDetail, uploadClient);
		}
		if (GetNewJobsJob.class.getCanonicalName().equals(jobDetail.getJobClass())) {
			return new GetNewJobsJob(jobDetail, getJobsClient);
		}
		throw new Exception(jobDetail.getJobClass() + " is not supported");
	}
	
}
