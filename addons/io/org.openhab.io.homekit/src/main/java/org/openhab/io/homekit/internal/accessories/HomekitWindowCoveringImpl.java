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

import java.util.concurrent.CompletableFuture;

import org.eclipse.smarthome.core.items.GenericItem;
import org.eclipse.smarthome.core.items.ItemRegistry;
import org.eclipse.smarthome.core.items.MetadataRegistry;
import org.eclipse.smarthome.core.library.items.DimmerItem;
import org.eclipse.smarthome.core.library.items.RollershutterItem;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.openhab.io.homekit.internal.HomekitAccessoryUpdater;
import org.openhab.io.homekit.internal.HomekitTaggedItem;

import com.beowulfe.hap.HomekitCharacteristicChangeCallback;
import com.beowulfe.hap.accessories.WindowCovering;
import com.beowulfe.hap.accessories.properties.WindowCoveringPositionState;

/**
 *
 * @author epike - Initial contribution
 */
public class HomekitWindowCoveringImpl extends AbstractHomekitAccessoryImpl<GenericItem> implements WindowCovering {
    private boolean inverted;

    public HomekitWindowCoveringImpl(HomekitTaggedItem taggedItem, ItemRegistry itemRegistry,
            MetadataRegistry metadataRegistry, HomekitAccessoryUpdater updater) {
        super(taggedItem, itemRegistry, metadataRegistry, updater, GenericItem.class);
        Object invertedMetadata = getMetadataSetting("inverted");
        if (invertedMetadata instanceof Boolean) {
            this.inverted = (Boolean) invertedMetadata;
        } else {
            this.inverted = false;
        }
    }

    @Override
    public CompletableFuture<Integer> getCurrentPosition() {
        PercentType value = getItem().getStateAs(PercentType.class);
        if (value == null) {
            return CompletableFuture.completedFuture(null);
        }
        int result = value.intValue();
        if (!inverted) {
            result = 100 - result;
        }
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public CompletableFuture<WindowCoveringPositionState> getPositionState() {
        return CompletableFuture.completedFuture(WindowCoveringPositionState.STOPPED);
    }

    @Override
    public CompletableFuture<Integer> getTargetPosition() {
        return getCurrentPosition();
    }

    @Override
    public CompletableFuture<Void> setTargetPosition(int value) throws Exception {
        int trueValue = value;
        if (!inverted) {
            trueValue = 100 - value;
        }

        if (getItem() instanceof RollershutterItem) {
            ((RollershutterItem) getItem()).send(new PercentType(trueValue));
        } else if (getItem() instanceof DimmerItem) {
            ((DimmerItem) getItem()).send(new PercentType(trueValue));
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void subscribeCurrentPosition(HomekitCharacteristicChangeCallback callback) {
        getUpdater().subscribe(getItem(), callback);
    }

    @Override
    public void subscribePositionState(HomekitCharacteristicChangeCallback callback) {
        // Not implemented
    }

    @Override
    public void subscribeTargetPosition(HomekitCharacteristicChangeCallback callback) {
        getUpdater().subscribe(getItem(), "targetPosition", callback);
    }

    @Override
    public void unsubscribeCurrentPosition() {
        getUpdater().unsubscribe(getItem());
    }

    @Override
    public void unsubscribePositionState() {
        // Not implemented
    }

    @Override
    public void unsubscribeTargetPosition() {
        getUpdater().unsubscribe(getItem(), "targetPosition");
    }
}
