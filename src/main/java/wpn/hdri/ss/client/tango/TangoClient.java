/*
 * The main contributor to this project is Institute of Materials Research,
 * Helmholtz-Zentrum Geesthacht,
 * Germany.
 *
 * This project is a contribution of the Helmholtz Association Centres and
 * Technische Universitaet Muenchen to the ESS Design Update Phase.
 *
 * The project's funding reference is FKZ05E11CG1.
 *
 * Copyright (c) 2012. Institute of Materials Research,
 * Helmholtz-Zentrum Geesthacht,
 * Germany.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA
 */

package wpn.hdri.ss.client.tango;

import org.apache.log4j.Logger;
import wpn.hdri.ss.client.Client;
import wpn.hdri.ss.client.ClientException;
import wpn.hdri.ss.data.Timestamp;
import wpn.hdri.ss.engine.ReadAttributeTask;
import wpn.hdri.tango.proxy.TangoAttributeInfoWrapper;
import wpn.hdri.tango.proxy.TangoEvent;
import wpn.hdri.tango.proxy.TangoProxyException;
import wpn.hdri.tango.proxy.TangoProxyWrapper;

import javax.annotation.concurrent.NotThreadSafe;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Igor Khokhriakov <igor.khokhriakov@hzg.de>
 * @since 27.04.12
 */
@NotThreadSafe
public class TangoClient extends Client {
    private final TangoProxyWrapper proxy;
    private Map<String, Integer> listeners = new HashMap<String, Integer>();

    public TangoClient(String deviceName, TangoProxyWrapper proxy) {
        super(deviceName);
        this.proxy = proxy;
    }

    /**
     * Reads value and time of the attribute specified by name
     *
     * @param attrName attribute name
     * @param <T>
     * @return
     * @throws ClientException
     */
    @Override
    public <T> Map.Entry<T, Timestamp> readAttribute(String attrName) throws ClientException {
        try {
            Map.Entry<T, Long> entry = proxy.readAttributeValueAndTime(attrName);
            return new AbstractMap.SimpleImmutableEntry<T, Timestamp>(entry.getKey(), new Timestamp(entry.getValue()));
        } catch (TangoProxyException devFailed) {
            throw new ClientException("Exception in " + proxy.getName(), devFailed);
        }
    }

    @Override
    public <T> void writeAttribute(String attrName, T value) throws ClientException {
        try {
            proxy.writeAttribute(attrName, value);
        } catch (TangoProxyException devFailed) {
            throw new ClientException("Failed to write value[" + value.toString() + "] to " + proxy.getName() + "/" + attrName, devFailed);
        }
    }

    @Override
    public void subscribeEvent(final String attrName, final ReadAttributeTask cbk) throws ClientException {
        try {
            int eventId = proxy.subscribeEvent(attrName, TangoEvent.CHANGE, cbk);
            listeners.put(attrName, eventId);
        } catch (TangoProxyException devFailed) {
            throw new ClientException("Exception in " + proxy.getName(), devFailed);
        }
    }

    /**
     * @param attrName attribute name to check
     * @return true if attribute is ok, false otherwise
     */
    @Override
    public boolean checkAttribute(String attrName) {
        return proxy.checkAttribute(attrName);
    }

    @Override
    public Class<?> getAttributeClass(String attrName) throws ClientException {
        try {
            TangoAttributeInfoWrapper attributeInfo = proxy.getAttributeInfo(attrName);
            return attributeInfo.getClazz();
        } catch (TangoProxyException devFailed) {
            throw new ClientException("Exception in " + proxy.getName(), devFailed);
        }
    }

    @Override
    public void unsubscribeEvent(String attrName) throws ClientException {
        int eventId = listeners.get(attrName);
        try {
            proxy.unsubscribeEvent(eventId);
        } catch (TangoProxyException devFailed) {
            throw new ClientException("Can not unsubscribe event for " + attrName, devFailed);
        }
    }

    @Override
    public void printAttributeInfo(String name, Logger logger) {
        try {
            TangoAttributeInfoWrapper info = proxy.getAttributeInfo(name);
            logger.info("Information for attribute " + proxy.getName() + "/" + name);
            logger.info("Data format:" + info.getFormat().toString());
            logger.info("Data type:" + info.getType().toString());
            logger.info("Java data type match:" + info.getClazz().getSimpleName());

        } catch (TangoProxyException e) {
            logger.error("Can not print attribute info for " + name, e);
        }
    }
}