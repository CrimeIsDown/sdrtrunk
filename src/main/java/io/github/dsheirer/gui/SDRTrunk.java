/*
 * *****************************************************************************
 * Copyright (C) 2014-2024 Dennis Sheirer
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
 * ****************************************************************************
 */
package io.github.dsheirer.gui;

import io.github.dsheirer.controller.channel.Channel;
import io.github.dsheirer.controller.channel.ChannelException;
import io.github.dsheirer.controller.channel.ChannelModel;
import io.github.dsheirer.controller.channel.ChannelProcessingManager;
import io.github.dsheirer.log.ApplicationLog;
import io.github.dsheirer.map.MapService;
import io.github.dsheirer.module.log.EventLogManager;
import io.github.dsheirer.monitor.DiagnosticMonitor;
import io.github.dsheirer.monitor.ResourceMonitor;
import io.github.dsheirer.playlist.PlaylistManager;
import io.github.dsheirer.preference.UserPreferences;
import io.github.dsheirer.properties.SystemProperties;
import io.github.dsheirer.record.AudioRecordingManager;
import io.github.dsheirer.source.tuner.manager.TunerManager;
import io.github.dsheirer.util.ThreadPool;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import java.awt.EventQueue;
import java.awt.GraphicsEnvironment;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"io.github.dsheirer"})
@EnableJpaRepositories("io.github.dsheirer.audio.call")
@EntityScan("io.github.dsheirer.audio.call")
public class SDRTrunk
{
    private final static Logger mLog = LoggerFactory.getLogger(SDRTrunk.class);
    @Resource
    private ApplicationLog mApplicationLog;
    @Resource
    private AudioRecordingManager mAudioRecordingManager;
    private AudioStreamingManager mAudioStreamingManager;
    private BroadcastStatusPanel mBroadcastStatusPanel;
    private ControllerPanel mControllerPanel;
    private DiagnosticMonitor mDiagnosticMonitor;
    private IconModel mIconModel = new IconModel();
    private PlaylistManager mPlaylistManager;
    private SettingsManager mSettingsManager;
    private SpectralDisplayPanel mSpectralPanel;
    private JFrame mMainGui;
    private JideSplitPane mSplitPane;
    private JavaFxWindowManager mJavaFxWindowManager;
    private UserPreferences mUserPreferences = new UserPreferences();
    @Resource
    private ChannelModel mChannelModel;
    @Resource
    private ChannelProcessingManager mChannelProcessingManager;
    @Resource
    private TunerManager mTunerManager;
    @Resource
    private UserPreferences mUserPreferences;
    @Resource //SpringUiFactory does not instantiate this instance when headless=true
    private SDRTrunkUI mSDRTrunkUI;

    /**
     * Constructs an instance of the SDRTrunk application
     */
    public SDRTrunk()
    {
    }


    @PostConstruct
    public void postConstruct()
    {
        ThreadPool.logSettings();

        //Log current properties setting
        SystemProperties.getInstance().logCurrentSettings();

        //Register FontAwesome so we can use the fonts in Swing windows
        IconFontSwing.register(FontAwesome.getIconFont());

        mTunerManager = new TunerManager(mUserPreferences);
        mTunerManager.start();

        mSettingsManager = new SettingsManager();

        AliasModel aliasModel = new AliasModel();
        EventLogManager eventLogManager = new EventLogManager(aliasModel, mUserPreferences);
        mPlaylistManager = new PlaylistManager(mUserPreferences, mTunerManager, aliasModel, eventLogManager, mIconModel);

        boolean headless = GraphicsEnvironment.isHeadless();

        mDiagnosticMonitor = new DiagnosticMonitor(mUserPreferences, mPlaylistManager.getChannelProcessingManager(),
                mTunerManager, headless);
        mDiagnosticMonitor.start();

        if(!headless)
        {
            mJavaFxWindowManager = new JavaFxWindowManager(mUserPreferences, mTunerManager, mPlaylistManager);
        }

        CalibrationManager calibrationManager = CalibrationManager.getInstance(mUserPreferences);
        final boolean calibrating = !calibrationManager.isCalibrated() &&
            !mUserPreferences.getVectorCalibrationPreference().isHideCalibrationDialog();

        new ChannelSelectionManager(mPlaylistManager.getChannelModel());

        AudioPlaybackManager audioPlaybackManager = new AudioPlaybackManager(mUserPreferences);

        mAudioRecordingManager = new AudioRecordingManager(mUserPreferences);
        mAudioRecordingManager.start();

        mAudioStreamingManager = new AudioStreamingManager(mPlaylistManager.getBroadcastModel(), BroadcastFormat.MP3,
            mUserPreferences);
        mAudioStreamingManager.start();

        DuplicateCallDetector duplicateCallDetector = new DuplicateCallDetector(mUserPreferences);

        mPlaylistManager.getChannelProcessingManager().addAudioSegmentListener(duplicateCallDetector);
        mPlaylistManager.getChannelProcessingManager().addAudioSegmentListener(audioPlaybackManager);
        mPlaylistManager.getChannelProcessingManager().addAudioSegmentListener(mAudioRecordingManager);
        mPlaylistManager.getChannelProcessingManager().addAudioSegmentListener(mAudioStreamingManager);

        MapService mapService = new MapService(mIconModel);
        mPlaylistManager.getChannelProcessingManager().addDecodeEventListener(mapService);

        mNowPlayingDetailsVisible = mPreferences.getBoolean(PREFERENCE_NOW_PLAYING_DETAILS_VISIBLE, true);

        if(!GraphicsEnvironment.isHeadless())
        {
            mControllerPanel = new ControllerPanel(mPlaylistManager, audioPlaybackManager, mIconModel, mapService,
                    mSettingsManager, mTunerManager, mUserPreferences, mNowPlayingDetailsVisible);
        }

        mSpectralPanel = new SpectralDisplayPanel(mPlaylistManager, mSettingsManager, mTunerManager.getDiscoveredTunerModel());

        TunerSpectralDisplayManager tunerSpectralDisplayManager = new TunerSpectralDisplayManager(mSpectralPanel,
            mPlaylistManager, mSettingsManager, mTunerManager.getDiscoveredTunerModel());
        mTunerManager.getDiscoveredTunerModel().addListener(tunerSpectralDisplayManager);
        mTunerManager.getDiscoveredTunerModel().addListener(this);

        mPlaylistManager.init();

        if(GraphicsEnvironment.isHeadless())
        {
            mLog.info("starting main application in headless mode");
        }
        else
        {
            mLog.info("starting main application with gui");
            if(mSDRTrunkUI != null)
            {
                EventQueue.invokeLater(() -> mSDRTrunkUI.setVisible(true));
            }
            else
            {
                mLog.error("SDRTrunk user interface is null - can't start UI");
            }
        }
    }

    /**
     * Initialize the contents of the frame.
     */
    private void initGUI()
    {
        mMainGui.setLayout(new MigLayout("insets 0 0 0 0 ", "[grow,fill]", "[grow,fill]0[shrink 0]"));

        /**
         * Setup main JFrame window
         */
        mTitle = SystemProperties.getInstance().getApplicationName();
        mMainGui.setTitle(mTitle);

        Point location = mUserPreferences.getSwingPreference().getLocation(WINDOW_FRAME_IDENTIFIER);
        if(location != null)
        {
            mMainGui.setLocation(location);
        }
        else
        {
            mMainGui.setLocationRelativeTo(null);
        }
        mMainGui.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mMainGui.addWindowListener(new ShutdownMonitor());

        Dimension dimension = mUserPreferences.getSwingPreference().getDimension(WINDOW_FRAME_IDENTIFIER);

        mSpectralPanel.setPreferredSize(new Dimension(1280, 300));
        mControllerPanel.setPreferredSize(new Dimension(1280, 500));

        if(dimension != null)
        {
            Dimension spectral = mUserPreferences.getSwingPreference().getDimension(SPECTRAL_PANEL_IDENTIFIER);
            if(spectral != null)
            {
                mSpectralPanel.setSize(spectral);
            }

            Dimension controller = mUserPreferences.getSwingPreference().getDimension(CONTROLLER_PANEL_IDENTIFIER);
            if(controller != null)
            {
                mControllerPanel.setSize(controller);
            }

            mMainGui.setSize(dimension);

            if(mUserPreferences.getSwingPreference().getMaximized(WINDOW_FRAME_IDENTIFIER, false))
            {
                mMainGui.setExtendedState(JFrame.MAXIMIZED_BOTH);
            }
        }
        else
        {
            mMainGui.setSize(new Dimension(1280, 800));
        }

        mSplitPane = new JideSplitPane(JideSplitPane.VERTICAL_SPLIT);
        mSplitPane.setDividerSize(5);
        mSplitPane.add(mSpectralPanel);
        mSplitPane.add(mControllerPanel);

        mBroadcastStatusVisible = mPreferences.getBoolean(PREFERENCE_BROADCAST_STATUS_VISIBLE, false);

        //Show broadcast status panel when user requests - disabled by default
        if(mBroadcastStatusVisible)
        {
            mSplitPane.add(getBroadcastStatusPanel());
        }

        mMainGui.add(mSplitPane, "cell 0 0,span,grow");

        mResourceMonitor.start();
        mResourceStatusVisible = mPreferences.getBoolean(PREFERENCE_RESOURCE_STATUS_VISIBLE, true);
        if(mResourceStatusVisible)
        {
            mMainGui.add(getResourceStatusPanel(), "span,growx");
        }

        /**
         * Menu items
         */
        JMenuBar menuBar = new JMenuBar();
        mMainGui.setJMenuBar(menuBar);

        JMenu fileMenu = new JMenu("File");
        menuBar.add(fileMenu);

        JMenuItem processingStatusReportMenuItem = new JMenuItem("Processing Diagnostic Report");
        processingStatusReportMenuItem.addActionListener(e -> {
            try
            {
                Path path = mDiagnosticMonitor.generateProcessingDiagnosticReport("User initiated diagnostic report");

                JOptionPane.showMessageDialog(mMainGui, "Report created: " +
                        path.toString(), "Processing Status Report Created", JOptionPane.INFORMATION_MESSAGE);
            }
            catch(IOException ioe)
            {
                mLog.error("Error creating processing status report file", ioe);
                JOptionPane.showMessageDialog(mMainGui, "Unable to create report file.  Please " +
                        "see application log for details.", "Processing Status Report Failed", JOptionPane.ERROR_MESSAGE);
            }
        });

        JMenuItem threadDumpReportMenuItem = new JMenuItem("Thread Dump Report");
        threadDumpReportMenuItem.addActionListener(e -> {
            try
            {
                Path path = mDiagnosticMonitor.generateThreadDumpReport();

                JOptionPane.showMessageDialog(mMainGui, "Report created: " +
                        path.toString(), "Thread Dump Report Created", JOptionPane.INFORMATION_MESSAGE);
            }
            catch(IOException ioe)
            {
                mLog.error("Error creating thread dump report file", ioe);
                JOptionPane.showMessageDialog(mMainGui, "Unable to create report file.  Please " +
                        "see application log for details.", "Thread Dump Report Failed", JOptionPane.ERROR_MESSAGE);
            }
        });

        JMenu diagnosticMenu = new JMenu(("Reports"));
        diagnosticMenu.add(processingStatusReportMenuItem);
        diagnosticMenu.add(threadDumpReportMenuItem);
        fileMenu.add(diagnosticMenu);
        fileMenu.add(new JSeparator(JSeparator.HORIZONTAL));

        JMenuItem exitMenu = new JMenuItem("Exit");
        exitMenu.addActionListener(event -> {
                processShutdown();
                System.exit(0);
            }
        );

        fileMenu.add(exitMenu);

        JMenu viewMenu = new JMenu("View");

        JMenuItem viewPlaylistItem = new JMenuItem("Playlist Editor");
        viewPlaylistItem.setIcon(IconFontSwing.buildIcon(FontAwesome.PLAY_CIRCLE_O, 12));
        viewPlaylistItem.addActionListener(e -> MyEventBus.getGlobalEventBus().post(new ViewPlaylistRequest()));
        viewMenu.add(viewPlaylistItem);

        viewMenu.add(new JSeparator());

        JMenuItem viewApplicationLogsMenu = new JMenuItem("Application Log Files");
        viewApplicationLogsMenu.setIcon(IconFontSwing.buildIcon(FontAwesome.FOLDER_OPEN_O, 12));
        viewApplicationLogsMenu.addActionListener(arg0 -> {
            File logsDirectory = mUserPreferences.getDirectoryPreference().getDirectoryApplicationLog().toFile();
            try
            {
                Desktop.getDesktop().open(logsDirectory);
            }
            catch(Exception e)
            {
                mLog.error("Couldn't open file explorer");

                JOptionPane.showMessageDialog(mMainGui,
                        "Can't launch file explorer - files are located at: " + logsDirectory,
                        "Can't launch file explorer",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
        viewMenu.add(viewApplicationLogsMenu);

        JMenuItem viewRecordingsMenuItem = new JMenuItem("Audio Recordings");
        viewRecordingsMenuItem.setIcon(IconFontSwing.buildIcon(FontAwesome.FOLDER_OPEN_O, 12));
        viewRecordingsMenuItem.addActionListener(arg0 -> {
            File recordingsDirectory = mUserPreferences.getDirectoryPreference().getDirectoryRecording().toFile();

            try
            {
                Desktop.getDesktop().open(recordingsDirectory);
            }
            catch(Exception e)
            {
                mLog.info("Auto-starting channel " + channel.getName());
                mChannelProcessingManager.start(channel);
            }
            catch(ChannelException ce)
            {
                mLog.error("Channel: " + channel.getName() + " auto-start failed: " + ce.getMessage());
            }
        }
    }

    /**
     * Performs shutdown operations
     */
    @PreDestroy
    public void preDestroy()
    {
        mLog.info("Application shutdown started ...");
        mDiagnosticMonitor.stop();
        mUserPreferences.getSwingPreference().setLocation(WINDOW_FRAME_IDENTIFIER, mMainGui.getLocation());
        mUserPreferences.getSwingPreference().setDimension(WINDOW_FRAME_IDENTIFIER, mMainGui.getSize());
        mUserPreferences.getSwingPreference().setMaximized(WINDOW_FRAME_IDENTIFIER,
            (mMainGui.getExtendedState() & JFrame.MAXIMIZED_BOTH) == JFrame.MAXIMIZED_BOTH);
        mUserPreferences.getSwingPreference().setDimension(SPECTRAL_PANEL_IDENTIFIER, mSpectralPanel.getSize());
        mUserPreferences.getSwingPreference().setDimension(CONTROLLER_PANEL_IDENTIFIER, mControllerPanel.getSize());
        mJavaFxWindowManager.shutdown();
        mLog.info("Stopping channels ...");
        mChannelProcessingManager.shutdown();
        mAudioRecordingManager.stop();
        mLog.info("Stopping tuners ...");
        mTunerManager.stop();
        mLog.info("Shutdown complete.");
    }

    /**
     * Monitors the SDRTrunkUI window for shutdown so that we can terminate the overall SDRTrunk application.
     */
    public class ShutdownMonitor extends WindowAdapter
    {
        @Override
        public void windowClosing(WindowEvent e)
        {
            preDestroy();
        }
    }

    /**
     * Launch the application.
     */
    public static void main(String[] args)
    {
        boolean headless = GraphicsEnvironment.isHeadless();

        //Set the user's preferred location for the database prior to starting the application
//        String dbpath = new UserPreferences().getDirectoryPreference().getDirectoryApplicationRoot().toString();
        String dbpath = new UserPreferences().getDirectoryPreference().getDirectoryDatabase().getParent().toString();
        System.setProperty("derby.system.home", dbpath);

        ConfigurableApplicationContext context = new SpringApplicationBuilder(SDRTrunk.class)
                .bannerMode(Banner.Mode.OFF)
                .headless(headless)
                .web(WebApplicationType.NONE)
                .registerShutdownHook(true)
                .run(args);
    }
}
