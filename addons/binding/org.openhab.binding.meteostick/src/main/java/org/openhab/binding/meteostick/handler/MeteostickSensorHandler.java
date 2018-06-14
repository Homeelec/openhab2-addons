/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.meteostick.handler;

import static org.eclipse.smarthome.core.library.unit.MetricPrefix.MILLI;
import static org.eclipse.smarthome.core.library.unit.SIUnits.*;
import static org.eclipse.smarthome.core.library.unit.SmartHomeUnits.*;
import static org.openhab.binding.meteostick.MeteostickBindingConstants.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.QuantityType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.ThingStatusInfo;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link MeteostickSensorHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Chris Jackson - Initial contribution
 */
public class MeteostickSensorHandler extends BaseThingHandler implements MeteostickEventListener {
    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES = Collections.singleton(THING_TYPE_DAVIS);

    private final Logger logger = LoggerFactory.getLogger(MeteostickSensorHandler.class);

    private int channel = 0;
    private BigDecimal spoon = new BigDecimal(PARAMETER_SPOON_DEFAULT);
    private MeteostickBridgeHandler bridgeHandler;
    private SlidingTimeWindow rainHourlyWindow = new SlidingTimeWindow(HOUR_IN_MSEC);
    private ScheduledFuture<?> rainHourlyJob;
    private ScheduledFuture<?> offlineTimerJob;

    private Date lastData;

    public MeteostickSensorHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        logger.debug("Initializing MeteoStick handler.");

        channel = ((BigDecimal) getConfig().get(PARAMETER_CHANNEL)).intValue();

        spoon = (BigDecimal) getConfig().get(PARAMETER_SPOON);
        if (spoon == null) {
            spoon = new BigDecimal(PARAMETER_SPOON_DEFAULT);
        }
        logger.debug("Initializing MeteoStick handler - Channel {}, Spoon size {} mm.", channel, spoon);

        Runnable pollingRunnable = () -> {
            BigDecimal rainfall = BigDecimal.valueOf(rainHourlyWindow.getTotal()).multiply(spoon);
            rainfall.setScale(1, RoundingMode.DOWN);
            updateState(new ChannelUID(getThing().getUID(), CHANNEL_RAIN_LASTHOUR),
                    new QuantityType<>(rainfall, MILLI(METRE)));
        };

        // Scheduling a job on each hour to update the last hour rainfall
        long start = HOUR_IN_SEC - ((System.currentTimeMillis() % HOUR_IN_MSEC) / 1000);
        rainHourlyJob = scheduler.scheduleWithFixedDelay(pollingRunnable, start, HOUR_IN_SEC, TimeUnit.SECONDS);

        updateStatus(ThingStatus.UNKNOWN);
    }

    @Override
    public void dispose() {
        if (rainHourlyJob != null) {
            rainHourlyJob.cancel(true);
        }

        if (offlineTimerJob != null) {
            offlineTimerJob.cancel(true);
        }

        if (bridgeHandler != null) {
            bridgeHandler.unsubscribeEvents(channel, this);
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
    }

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        logger.debug("MeteoStick handler {}: bridgeStatusChanged to {}", channel, bridgeStatusInfo);
        if (bridgeStatusInfo.getStatus() != ThingStatus.ONLINE) {
            logger.debug("MeteoStick handler {}: bridgeStatusChanged but bridge offline", channel);
            updateStatus(ThingStatus.OFFLINE);
            return;
        }

        bridgeHandler = (MeteostickBridgeHandler) getBridge().getHandler();

        if (channel != 0) {
            if (bridgeHandler != null) {
                bridgeHandler.subscribeEvents(channel, this);
            }
        }

        // Put the thing online and start our "no data" timer
        updateStatus(ThingStatus.ONLINE);
        startTimeoutCheck();
    }

    private void processSignalStrength(String dbmString) {
        double dbm = Double.parseDouble(dbmString);
        int strength;

        if (dbm > -60) {
            strength = 4;
        } else if (dbm > -70) {
            strength = 3;
        } else if (dbm > -80) {
            strength = 2;
        } else if (dbm > -90) {
            strength = 1;
        } else {
            strength = 0;
        }

        updateState(new ChannelUID(getThing().getUID(), CHANNEL_SIGNAL_STRENGTH), new DecimalType(strength));
    }

    private void processBattery(boolean batteryLow) {
        OnOffType state = batteryLow ? OnOffType.ON : OnOffType.OFF;

        updateState(new ChannelUID(getThing().getUID(), CHANNEL_LOW_BATTERY), state);
    }

    @Override
    public void onDataReceived(String[] data) {
        logger.debug("MeteoStick received channel {}: {}", channel, data);
        updateStatus(ThingStatus.ONLINE);
        lastData = new Date();

        startTimeoutCheck();

        switch (data[0]) {
            case "R": // Rain
                int rain = Integer.parseInt(data[2]);
                updateState(new ChannelUID(getThing().getUID(), CHANNEL_RAIN_RAW), new DecimalType(rain));
                processSignalStrength(data[3]);
                processBattery(data.length == 5);

                rainHourlyWindow.put(rain);

                BigDecimal rainfall = BigDecimal.valueOf(rainHourlyWindow.getTotal()).multiply(spoon);
                rainfall.setScale(1, RoundingMode.DOWN);
                updateState(new ChannelUID(getThing().getUID(), CHANNEL_RAIN_CURRENTHOUR),
                        new QuantityType<>(rainfall, MILLI(METRE)));
                break;
            case "W": // Wind
                updateState(new ChannelUID(getThing().getUID(), CHANNEL_WIND_SPEED),
                        new QuantityType<>(new BigDecimal(data[2]), METRE_PER_SECOND));
                updateState(new ChannelUID(getThing().getUID(), CHANNEL_WIND_DIRECTION),
                        new QuantityType<>(Integer.parseInt(data[3]), DEGREE_ANGLE));

                processSignalStrength(data[4]);
                processBattery(data.length == 6);
                break;
            case "T": // Temperature
                BigDecimal temperature = new BigDecimal(data[2]);
                updateState(new ChannelUID(getThing().getUID(), CHANNEL_OUTDOOR_TEMPERATURE),
                        new QuantityType<>(temperature.setScale(1), CELSIUS));

                BigDecimal humidity = new BigDecimal(data[3]);
                updateState(new ChannelUID(getThing().getUID(), CHANNEL_HUMIDITY),
                        new DecimalType(humidity.setScale(1)));

                processSignalStrength(data[4]);
                processBattery(data.length == 6);
                break;
            case "P": // Solar panel power
                BigDecimal power = new BigDecimal(data[2]);
                updateState(new ChannelUID(getThing().getUID(), CHANNEL_SOLAR_POWER),
                        new DecimalType(power.setScale(1)));

                processSignalStrength(data[3]);
                processBattery(data.length == 5);
                break;
        }
    }

    class SlidingTimeWindow {
        long period = 0;
        private final Map<Long, Integer> storage = new TreeMap<>();

        /**
         *
         * @param period window period in milliseconds
         */
        public SlidingTimeWindow(long period) {
            this.period = period;
        }

        public void put(int value) {
            storage.put(System.currentTimeMillis(), value);
        }

        public int getTotal() {
            int least = -1;
            int total = 0;

            long old = System.currentTimeMillis() - period;
            for (Iterator<Long> iterator = storage.keySet().iterator(); iterator.hasNext();) {
                long time = iterator.next();
                if (time < old) {
                    // Remove
                    iterator.remove();
                    continue;
                }

                int value = storage.get(time);
                if (least == -1) {
                    least = value;
                    continue;
                }

                if (value < least) {
                    total = 256 - least + value;
                } else {
                    total = value - least;
                }
            }

            return total;
        }
    }

    private synchronized void startTimeoutCheck() {
        Runnable pollingRunnable = () -> {
            String detail;
            if (lastData == null) {
                detail = "No data received";
            } else {
                detail = "No data received since " + lastData.toString();
            }
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, detail);
        };

        if (offlineTimerJob != null) {
            offlineTimerJob.cancel(true);
        }

        // Scheduling a job on each hour to update the last hour rainfall
        offlineTimerJob = scheduler.schedule(pollingRunnable, 90, TimeUnit.SECONDS);
    }
}
