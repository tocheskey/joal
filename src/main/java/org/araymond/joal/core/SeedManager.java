package org.araymond.joal.core;

import com.google.common.eventbus.Subscribe;
import org.araymond.joal.core.client.emulated.EmulatedClientFactory;
import org.araymond.joal.core.config.ConfigProvider;
import org.araymond.joal.core.torrent.watcher.TorrentFileProvider;
import org.araymond.joal.core.ttorent.client.Client;
import org.araymond.joal.core.ttorent.client.MockedTorrent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

/**
 * Created by raymo on 27/01/2017.
 */
public class SeedManager {

    private static final Logger logger = LoggerFactory.getLogger(SeedManager.class);

    private final TorrentFileProvider torrentFileProvider;
    private final EmulatedClientFactory emulatedClientFactory;
    private final Random rand;

    private File currentTorrent;
    private Client currentClient;
    private ActionOnStopSeeding actionOnStopSeeding;
    private boolean stop = false;

    public SeedManager(final TorrentFileProvider torrentFileProvider, final EmulatedClientFactory emulatedClientFactory) {
        this.torrentFileProvider = torrentFileProvider;
        this.emulatedClientFactory = emulatedClientFactory;
        this.rand = new Random();
    }

    public void startSeeding() throws IOException, NoSuchAlgorithmException, InterruptedException {
        this.torrentFileProvider.start();
        while (!this.stop) {
            this.actionOnStopSeeding = ActionOnStopSeeding.WAIT;

            this.currentTorrent = torrentFileProvider.getRandomTorrentFile();

            this.currentClient = new Client(
                    InetAddress.getLocalHost(),
                    MockedTorrent.fromFile(this.currentTorrent),
                    emulatedClientFactory.createClient()
            );
            this.currentClient.addEventBusListener(this);

            final int seedTimeInSeconds = getRandomizedSeedForInMinutes();
            logger.info("Start seeding for {} minutes.", seedTimeInSeconds);
            this.currentClient.share(seedTimeInSeconds * 60);
            this.currentClient.waitForCompletion();

            this.currentClient.removeEventBusListener(this);

            // TODO : investigate if stop is required or not.
            this.currentClient.stop();
            this.currentClient = null;
            if (!this.stop) {
                if (actionOnStopSeeding == ActionOnStopSeeding.WAIT) {
                    final int waitBetweenSeedInMinutes = getRandomizedWaitBetweenSeedInMinutes();
                    logger.info("Waiting {} minutes before seeding again.", waitBetweenSeedInMinutes);
                    Thread.sleep(getRandomizedWaitBetweenSeedInMinutes() * 60L * 1000L);
                } else {
                    // Wait 10 second in any cases
                    Thread.sleep(5000);
                }
            }
        }
    }

    private int getRandomizedSeedForInMinutes() {
        // TODO : ensure min won't go below 0
        final int minSeedFor = ConfigProvider.get().getSeedFor() - 15;
        final int maxSeedFor = ConfigProvider.get().getSeedFor() + 15;

        return rand.nextInt(maxSeedFor - minSeedFor) + minSeedFor;
    }

    private final int getRandomizedWaitBetweenSeedInMinutes() {
        final int minWaitBetweenSeed = ConfigProvider.get().getWaitBetweenSeed() - 15;
        final int maxWaitBetweenSeed = ConfigProvider.get().getWaitBetweenSeed() + 15;

        return rand.nextInt(maxWaitBetweenSeed - minWaitBetweenSeed) + maxWaitBetweenSeed;
    }

    public void stop() {
        this.stop = true;
        this.torrentFileProvider.stop();
        if (this.currentClient != null) {
            this.currentClient.stop();
        }
    }

    @Subscribe
    private void handleEvent(final Client.Event event) {
        if (event == Client.Event.ENCOUNTER_ZERO_PEER) {
            this.actionOnStopSeeding = ActionOnStopSeeding.RESTART_IMMEDIATLY;
            this.torrentFileProvider.moveToArchiveFolder(this.currentTorrent);
        }
    }

    private enum ActionOnStopSeeding {
        WAIT,
        RESTART_IMMEDIATLY
    }

}
