package org.araymond.joal.core;

import org.araymond.joal.core.client.emulated.BitTorrentClientProvider;
import org.araymond.joal.core.config.JoalConfigProvider;
import org.araymond.joal.core.torrent.watcher.TorrentFileProvider;
import org.araymond.joal.core.ttorent.client.Client;
import org.araymond.joal.core.ttorent.client.MockedTorrent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Random;

/**
 * Created by raymo on 27/01/2017.
 */
@Component
public class SeedManager implements LeecherAware {

    private static final Logger logger = LoggerFactory.getLogger(SeedManager.class);

    private final JoalConfigProvider configProvider;
    private final TorrentFileProvider torrentFileProvider;
    private final BitTorrentClientProvider bitTorrentClientProvider;
    private final Random rand;

    private MockedTorrent currentTorrent;
    private Client currentClient;
    private ActionOnStopSeeding actionOnStopSeeding;
    private boolean stop = false;


    @Inject
    public SeedManager(final JoalConfigProvider configProvider, final TorrentFileProvider torrentFileProvider, final BitTorrentClientProvider bitTorrentClientProvider) {
        this.configProvider = configProvider;
        this.torrentFileProvider = torrentFileProvider;
        this.bitTorrentClientProvider = bitTorrentClientProvider;
        this.rand = new Random();
    }

    public void startSeeding() throws IOException, InterruptedException {
        while (!this.stop) {
            this.actionOnStopSeeding = ActionOnStopSeeding.WAIT;

            this.currentTorrent = torrentFileProvider.getRandomTorrentFile();

            this.currentClient = new Client(
                    configProvider,
                    InetAddress.getLocalHost(),
                    this.currentTorrent,
                    bitTorrentClientProvider.generateNewClient(),
                    this
            );

            final int seedTimeInSeconds = getRandomizedSeedForInMinutes();
            logger.info("Start seeding for {} minutes.", seedTimeInSeconds);
            this.currentClient.share(seedTimeInSeconds * 60);
            this.currentClient.waitForCompletion();

            this.currentClient.stop();
            this.currentClient = null;
            if (!this.stop) {
                if (actionOnStopSeeding == ActionOnStopSeeding.WAIT) {
                    final int waitBetweenSeedInMinutes = getRandomizedWaitBetweenSeedInMinutes();
                    logger.info("Waiting {} minutes before seeding again.", waitBetweenSeedInMinutes);
                    Thread.sleep(getRandomizedWaitBetweenSeedInMinutes() * 60L * 1000L);
                } else {
                    // Wait 8 second in any cases
                    Thread.sleep(8000);
                }
            }
        }
    }

    @Override
    public void onNoLeechersAvailable() {
        logger.warn("0 peers are currently leeching, moving torrent to archived and restarting seed.");
        this.actionOnStopSeeding = ActionOnStopSeeding.RESTART_IMMEDIATLY;
        this.torrentFileProvider.moveToArchiveFolder(this.currentTorrent);
        this.currentClient.stop(false);
    }

    private int getRandomizedSeedForInMinutes() {
        // TODO : ensure min won't go below 0
        final int minSeedFor = configProvider.get().getSeedFor() - 15;
        final int maxSeedFor = configProvider.get().getSeedFor() + 15;

        return rand.nextInt(maxSeedFor - minSeedFor) + minSeedFor;
    }

    private int getRandomizedWaitBetweenSeedInMinutes() {
        // TODO : ensure won't go below 0
        final int minWaitBetweenSeed = configProvider.get().getWaitBetweenSeed() - 15;
        final int maxWaitBetweenSeed = configProvider.get().getWaitBetweenSeed() + 15;

        return rand.nextInt(maxWaitBetweenSeed - minWaitBetweenSeed) + maxWaitBetweenSeed;
    }

    public void stop() {
        logger.info("Gracefully shutting down SeedManager.");
        this.stop = true;
        if (this.currentClient != null) {
            this.currentClient.stop();
        }

        logger.info("SeedManager gracefully shut down.");
    }

    private enum ActionOnStopSeeding {
        WAIT,
        RESTART_IMMEDIATLY
    }

}