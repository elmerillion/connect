/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.donkey.server.controllers;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.mirth.connect.donkey.model.message.Status;
import com.mirth.connect.donkey.server.Donkey;
import com.mirth.connect.donkey.server.channel.Statistics;
import com.mirth.connect.donkey.server.data.DonkeyDao;

public class ChannelController {
    private static ChannelController instance;

    public static ChannelController getInstance() {
        synchronized (ChannelController.class) {
            if (instance == null) {
                try {
                    /*
                     * Eventually, plugins will be able to specify controller classes to override,
                     * see MIRTH-3351
                     */
                    instance = (ChannelController) Class.forName("com.mirth.connect.plugins.clusteringadvanced.server.ClusterDonkeyChannelController").newInstance();
                } catch (Exception e) {
                    instance = new ChannelController();
                }
            }

            return instance;
        }
    }

    private Statistics currentStats;
    private Statistics totalStats;
    private Donkey donkey = Donkey.getInstance();

    protected ChannelController() {}

    public void removeChannel(String channelId) {
        DonkeyDao dao = donkey.getDaoFactory().getDao();

        try {
            dao.removeChannel(channelId);
            dao.commit();
        } finally {
            dao.close();
        }
    }

    public void loadStatistics(String serverId) {
        DonkeyDao dao = donkey.getDaoFactory().getDao();

        try {
            currentStats = dao.getChannelStatistics(serverId);
            totalStats = dao.getChannelTotalStatistics(serverId);
        } finally {
            dao.close();
        }
    }

    public Statistics getStatistics() {
        return currentStats;
    }

    public Statistics getTotalStatistics() {
        return totalStats;
    }

    /**
     * Reset the statistics for the given channels/connectors and statuses
     * 
     * @param channelConnectorMap
     *            A Map of channel ids and lists of connector meta data ids
     * @param statuses
     *            A list of statuses
     */
    public void resetStatistics(Map<String, List<Integer>> channelConnectorMap, Set<Status> statuses) {
        DonkeyDao dao = donkey.getDaoFactory().getDao();

        try {
            for (Entry<String, List<Integer>> entry : channelConnectorMap.entrySet()) {
                String channelId = entry.getKey();
                List<Integer> metaDataIds = entry.getValue();

                for (Integer metaDataId : metaDataIds) {
                    dao.resetStatistics(channelId, metaDataId, statuses);

                    // Each update here must have its own transaction, otherwise deadlocks may occur.
                    dao.commit();
                }
            }
        } finally {
            dao.close();
        }
    }

    public void resetAllStatistics() {
        DonkeyDao dao = donkey.getDaoFactory().getDao();

        try {
            for (String channelId : dao.getLocalChannelIds().keySet()) {
                dao.resetAllStatistics(channelId);

                // Each update here must have its own transaction, otherwise deadlocks may occur.
                dao.commit();
            }
        } finally {
            dao.close();
        }
    }

    public Long getLocalChannelId(String channelId) {
        Long localChannelId = null;
        DonkeyDao dao = donkey.getDaoFactory().getDao();

        try {
            localChannelId = dao.getLocalChannelIds().get(channelId);
        } finally {
            dao.close();
        }

        if (localChannelId == null) {
            localChannelId = createChannel(channelId);
        }

        return localChannelId;
    }

    public void initChannelStorage(String channelId) {
        getLocalChannelId(channelId);
    }

    public boolean channelExists(String channelId) {
        DonkeyDao dao = donkey.getDaoFactory().getDao();

        try {
            return (dao.getLocalChannelIds().get(channelId) != null);
        } finally {
            dao.close();
        }
    }

    public void deleteAllMessages(String channelId) {
        DonkeyDao dao = donkey.getDaoFactory().getDao();

        try {
            if (dao.getLocalChannelIds().get(channelId) != null) {
                dao.deleteAllMessages(channelId);
            }

            dao.commit();
        } finally {
            dao.close();
        }
    }

    private synchronized long createChannel(String channelId) {
        DonkeyDao dao = donkey.getDaoFactory().getDao();

        try {
            Long localChannelId = dao.selectMaxLocalChannelId();
            if (localChannelId == null) {
                localChannelId = 1L;
            } else {
                localChannelId++;
            }

            dao.createChannel(channelId, localChannelId);
            dao.commit();

            return localChannelId;
        } finally {
            dao.close();
        }
    }
}
