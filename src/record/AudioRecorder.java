/*******************************************************************************
 * sdrtrunk
 * Copyright (C) 2014-2016 Dennis Sheirer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 *
 ******************************************************************************/
package record;

import audio.AudioPacket;
import audio.IAudioPacketListener;
import audio.metadata.AudioMetadata;
import module.Module;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sample.Listener;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AudioRecorder extends Module implements Listener<AudioPacket>, IAudioPacketListener
{
    private final static Logger mLog = LoggerFactory.getLogger(AudioRecorder.class);

    private LinkedBlockingQueue<AudioPacket> mAudioPacketQueue = new LinkedBlockingQueue<>(500);
    private List<AudioPacket> mPacketsToProcess = new ArrayList<>();

    private FileOutputStream mFileOutputStream;
    private AtomicBoolean mRunning = new AtomicBoolean();

    protected Path mPath;
    protected AudioMetadata mAudioMetadata;
    protected long mTimeRecordingStart;
    protected long mTimeLastPacketReceived;
    private BufferProcessor mBufferProcessor;
    private ScheduledFuture<?> mProcessorHandle;

    /**
     * Abstract audio recorder that implements audio packet queueing and threaded audio conversion/writing to a file
     *
     * @param path for the output recording
     */
    public AudioRecorder(Path path)
    {
        mPath = path;
    }

    /**
     * Path for the audio recording file
     */
    public Path getPath()
    {
        return mPath;
    }

    /**
     * Latest audio metadata received for this recording
     */
    public AudioMetadata getMetadata()
    {
        return mAudioMetadata;
    }

    /**
     * Timestamp of the last buffer received by this recorder - allows this recorder to be monitored for automatic
     * closure after a time period has elapsed.
     */
    public long getTimeLastPacketReceived()
    {
        return mTimeLastPacketReceived;
    }

    public long getTimeRecordingStart()
    {
        return mTimeRecordingStart;
    }

    /**
     * Implements the IAudioPacketListener interface and simply redirects to the Listener<AudioPacket> interface.
     * This is necessary since you can't have multiple methods with the same erasure (ie Listener<xxx>) in the
     * parent module class.
     */
    @Override
    public Listener<AudioPacket> getAudioPacketListener()
    {
        return this;
    }

    /**
     * Processes the audio packet and captures the latest AudioMetadata for the recording for easy access.
     */
    @Override
    public void receive(AudioPacket audioPacket)
    {
        if(mRunning.get())
        {
            mTimeRecordingStart = System.currentTimeMillis();
            mTimeLastPacketReceived = mTimeRecordingStart;

            if(audioPacket.hasAudioMetadata())
            {
                mAudioMetadata = audioPacket.getAudioMetadata();
            }

            boolean success = mAudioPacketQueue.offer(audioPacket);

            if (!success)
            {
                mLog.error("recorder buffer overflow - stopping recorder [" + getPath().toString() + "]");
                stop();
            }
        }
    }

    /**
     * File output stream for the current recording.  Intended to allow sub-classes to write binary data to the file.
     */
    protected OutputStream getOutputStream()
    {
        return null;
    }

    /**
     * Stops the recorder and closes out the recording file
     */
    public void stop()
    {
        if(mRunning.compareAndSet(true, false))
        {
            if (mProcessorHandle != null)
            {
                mProcessorHandle.cancel(true);
            }

            mProcessorHandle = null;

            processAudioPacketQueue();

            close();
        }
    }

    /**
     * Closes the recording file.
     */
    private void close()
    {
        if (mFileOutputStream != null)
        {
            try
            {
                mFileOutputStream.close();
            }
            catch (IOException e)
            {
                mLog.error("Error closing output stream", e);
            }
        }
    }


    /**
     * Records the list of audio packets in the sub-class specific audio format.
     */
    protected abstract void record(List<AudioPacket> audioPackets) throws IOException;

    /**
     * Starts this recorder as a scheduled thread running under the executor argument
     *
     * @param executor to use in scheduling audio conversion and file writes.
     */
    public void start(ScheduledExecutorService executor)
    {
        if (mRunning.compareAndSet(false, true))
        {
            mTimeLastPacketReceived = System.currentTimeMillis();

            if (mBufferProcessor == null)
            {
                mBufferProcessor = new BufferProcessor();
            }

            try
            {
                mFileOutputStream = new FileOutputStream(mPath.toFile());

                mLog.info("Created Audio Recording [" + getPath().toString() + "]");

				/* Schedule the handler to run every half second */
                mProcessorHandle = executor.scheduleAtFixedRate(mBufferProcessor, 0, 500, TimeUnit.MILLISECONDS);
            }
            catch (IOException io)
            {
                mLog.error("Error starting audio recorder [" + getPath().toString() + "]", io);

                mRunning.set(false);
            }
        }
    }

    /**
     * Processes the audio packet queue.
     */
    private void processAudioPacketQueue()
    {
        mAudioPacketQueue.drainTo(mPacketsToProcess);

        if (!mPacketsToProcess.isEmpty())
        {
            try
            {
                record(mPacketsToProcess);
            }
            catch(IOException ioe)
            {
                mLog.debug("Error while recording audio to [" + getPath().toString() + "] - stopping recorder");
                stop();
            }

            mPacketsToProcess.clear();
        }
    }

    /**
     * Disposes this audio recorder and prepares it for reclamation
     */
    @Override
    public void dispose()
    {
        stop();
    }

    /**
     * Not implemented.  Recorder modules are not appropriate for reset and reuse.
     */
    @Override
    public void reset()
    {
    }

    /**
     * Drains the audio packet queue and records the audio packets to file
     */
    public class BufferProcessor implements Runnable
    {
        private AtomicBoolean mProcessing = new AtomicBoolean();

        public void run()
        {
            if (mProcessing.compareAndSet(false, true))
            {
                processAudioPacketQueue();

                mProcessing.set(false);
            }
        }
    }
}
