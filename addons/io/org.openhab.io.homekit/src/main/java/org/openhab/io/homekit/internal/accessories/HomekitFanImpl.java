/**
 * Copyright (c) 2010-2019 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.io.homekit.internal.accessories;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.eclipse.smarthome.core.items.GenericItem;
import org.eclipse.smarthome.core.items.GroupItem;
import org.eclipse.smarthome.core.items.ItemRegistry;
import org.eclipse.smarthome.core.library.items.DimmerItem;
import org.eclipse.smarthome.core.library.items.SwitchItem;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.types.State;
import org.openhab.io.homekit.internal.HomekitAccessoryUpdater;
import org.openhab.io.homekit.internal.HomekitTaggedItem;

import com.beowulfe.hap.HomekitCharacteristicChangeCallback;
import com.beowulfe.hap.accessories.Fan;
import com.beowulfe.hap.accessories.characteristics.RotationDirection;
import com.beowulfe.hap.accessories.characteristics.RotationSpeed;

/**
 * Implements Fan using an Item that provides a On/Off and possibly a Percent state.
 *
 * @author Cody Cutrer - Initial contribution
 */
class HomekitFanImpl extends AbstractHomekitAccessoryImpl<GenericItem> implements Fan {

    public HomekitFanImpl(HomekitTaggedItem taggedItem, ItemRegistry itemRegistry, HomekitAccessoryUpdater updater) {
        super(taggedItem, itemRegistry, updater, GenericItem.class);
    }

    @Override
    public CompletableFuture<Boolean> getFanPower() {
        OnOffType state = getItem().getStateAs(OnOffType.class);
        return CompletableFuture.completedFuture(state == OnOffType.ON);
    }

    @Override
    public CompletableFuture<Void> setFanPower(boolean value) throws Exception {
        GenericItem item = getItem();
        if (item instanceof SwitchItem) {
            ((SwitchItem) item).send(value ? OnOffType.ON : OnOffType.OFF);
        } else if (item instanceof GroupItem) {
            ((GroupItem) item).send(value ? OnOffType.ON : OnOffType.OFF);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void subscribeFanPower(HomekitCharacteristicChangeCallback callback) {
        getUpdater().subscribe(getItem(), callback);
    }

    @Override
    public void unsubscribeFanPower() {
        getUpdater().unsubscribe(getItem());
    }

    @Override
    public CompletableFuture<Integer> getRotationSpeed() {
        State state = getItem().getStateAs(PercentType.class);
        if (state instanceof PercentType) {
            PercentType speed = (PercentType) state;
            return CompletableFuture.completedFuture(speed.intValue());
        } else {
            return CompletableFuture.completedFuture(null);
        }
    }

    @Override
    public CompletableFuture<Void> setRotationSpeed(Integer value) throws Exception {
        GenericItem item = getItem();
        if (item instanceof DimmerItem) {
            ((DimmerItem) item).send(new PercentType(value));
        } else if (item instanceof GroupItem) {
            ((GroupItem) item).send(new PercentType(value));
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void subscribeRotationSpeed(HomekitCharacteristicChangeCallback callback) {
        getUpdater().subscribe(getItem(), "speed", callback);
    }

    @Override
    public void unsubscribeRotationSpeed() {
        getUpdater().unsubscribe(getItem(), "speed");
    }

    @Override
    public Optional<RotationSpeed> getRotationSpeedCharacteristic() {
        if (getItem() instanceof DimmerItem) {
            return Optional.of(this);
        } else {
            Optional<RotationSpeed> result = Optional.empty();
            return result;
        }
    }

    @Override
    public Optional<RotationDirection> getRotationDirectionCharacteristic() {
        Optional<RotationDirection> result = Optional.empty();
        return result;
    }
}
