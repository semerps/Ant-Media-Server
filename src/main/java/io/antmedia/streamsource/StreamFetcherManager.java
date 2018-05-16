package io.antmedia.streamsource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.red5.logging.Red5LoggerFactory;
import org.red5.server.adapter.MultiThreadedApplicationAdapter;
import org.red5.server.api.scheduling.IScheduledJob;
import org.red5.server.api.scheduling.ISchedulingService;
import org.red5.server.api.scope.IScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import io.antmedia.AntMediaApplicationAdapter;
import io.antmedia.datastore.db.IDataStore;
import io.antmedia.datastore.db.types.Broadcast;
import io.antmedia.rest.model.Result;


/**
 * Organizes and checks stream fetcher and restarts them if it is required
 * @author davut
 *
 */
public class StreamFetcherManager {

	protected static Logger logger = LoggerFactory.getLogger(StreamFetcherManager.class);

	private int streamCheckerCount = 0;

	private ConcurrentLinkedQueue<StreamFetcher> streamFetcherList = new ConcurrentLinkedQueue<>();

	private int streamCheckerInterval = 10000;

	private ISchedulingService schedulingService;

	private IDataStore datastore;

	private IScope scope;

	private String streamFetcherScheduleJobName;


	public StreamFetcherManager(ISchedulingService schedulingService, IDataStore datastore,IScope scope) {


		this.schedulingService = schedulingService;
		this.datastore = datastore;
		this.scope=scope;
	}

	public int getStreamCheckerInterval() {
		return streamCheckerInterval;
	}


	public void setStreamCheckerInterval(int streamCheckerInterval) {
		this.streamCheckerInterval = streamCheckerInterval;
	}


	public Result startStreaming(Broadcast broadcast) {	

		Result result=new Result(false);

		try {
			StreamFetcher streamScheduler = new StreamFetcher(broadcast,scope);
			streamFetcherList.add(streamScheduler);
			streamScheduler.startStream();

			try {
				Thread.sleep(6000);
			} catch (InterruptedException e) {
				e.printStackTrace();
				Thread.currentThread().interrupt();
			}

			if(!streamScheduler.getCameraError().isSuccess()) {
				result=streamScheduler.getCameraError();
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}

		return result;
	}

	public void stopStreaming(Broadcast stream) {
		logger.warn("inside of stopStreaming");

		for (StreamFetcher streamScheduler : streamFetcherList) {
			if (streamScheduler.getStream().getStreamId().equals(stream.getStreamId())) {
				streamScheduler.stopStream();
				streamFetcherList.remove(streamScheduler);
				break;
			}

		}

	}

	public void startStreams(List<Broadcast> streams) {

		for (int i = 0; i < streams.size(); i++) {
			startStreaming(streams.get(i));
		}

		if (streamFetcherScheduleJobName != null) {
			schedulingService.removeScheduledJob(streamFetcherScheduleJobName);
		}

		streamFetcherScheduleJobName = schedulingService.addScheduledJobAfterDelay(streamCheckerInterval, new IScheduledJob() {

			@Override
			public void execute(ISchedulingService service) throws CloneNotSupportedException {

				if (streamFetcherList.size() > 0) {

					streamCheckerCount++;

					logger.warn("StreamFetcher Check Count  :" + streamCheckerCount);

					if (streamCheckerCount % 180 == 0) {

						for (StreamFetcher streamScheduler : streamFetcherList) {
							if (streamScheduler.isStreamAlive()) 
							{
								streamScheduler.stopStream();
							}
							streamScheduler.startStream();
						}

					} else {
						for (StreamFetcher streamScheduler : streamFetcherList) {
							Broadcast stream = streamScheduler.getStream();
							if (!streamScheduler.isStreamAlive()) {
								
								if (datastore != null && stream.getStreamId() != null) {
									logger.info("Updating stream status to finished, updating status of stream {}", stream.getStreamId() );
									datastore.updateStatus(stream.getStreamId() , 
											AntMediaApplicationAdapter.BROADCAST_STATUS_FINISHED);
								}
							}

							if (!streamScheduler.isThreadActive()) {
								streamScheduler.startStream();
							}
							else {
								logger.info("there is an active thread for {} so that new thread is not started", stream.getStreamId());
							}
						
						}
					}
				}
			}
		}, 5000);

		logger.info("StreamFetcherSchedule job name {}", streamFetcherScheduleJobName);
	}

	public IDataStore getDatastore() {
		return datastore;
	}

	public void setDatastore(IDataStore datastore) {
		this.datastore = datastore;
	}

	public ConcurrentLinkedQueue<StreamFetcher> getStreamFetcherList() {
		return streamFetcherList;
	}

	public void setStreamFetcherList(ConcurrentLinkedQueue<StreamFetcher> streamFetcherList) {
		this.streamFetcherList = streamFetcherList;
	}

}
