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

package wpn.hdri.ss.data.attribute;

import com.google.common.base.Objects;
import hzg.wpn.collection.Maps;
import org.apache.log4j.Logger;
import wpn.hdri.ss.data.Interpolation;
import wpn.hdri.ss.data.Timestamp;
import wpn.hdri.ss.data.Value;

import javax.annotation.concurrent.ThreadSafe;
import java.util.Map;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Stores values and corresponding read/write timestamps
 * <p/>
 * Implementation is thread safe but does not guarantee that underlying map won't be changed while reading
 * <p/>
 * This class contains most of the logic linked with storing values. The subclasses specify the way how a value is being added.
 *
 * @author Igor Khokhriakov <igor.khokhriakov@hzg.de>
 * @since 27.04.12
 */
@ThreadSafe
public abstract class Attribute<T> {
    protected static final Logger LOGGER = Logger.getLogger(Attribute.class);

    private final AttributeName name;

    private final AtomicReference<AttributeValue<T>> lastValue;
    private final ConcurrentNavigableMap<Timestamp, AttributeValue<T>> values = Maps.newConcurrentNavigableMap();

    private final Interpolation interpolation;

    private final AtomicLong size = new AtomicLong(0L);

    /**
     * Upon construction last value equals to Value.NULL
     *
     * @param deviceName
     * @param name
     * @param alias
     * @param interpolation
     */
    public Attribute(String deviceName, String name, String alias, Interpolation interpolation) {
        this.name = new AttributeName(deviceName, name, alias);
        this.lastValue = new AtomicReference<>(new AttributeValue<T>(this.name.getFullName(), alias, Value.NULL, Timestamp.now(), Timestamp.now()));

        this.interpolation = interpolation;
    }

    /**
     * Implementation not thread safe. But it is very unlikely that two thread will access the same attribute
     * in the same time.
     *
     * @param readTimestamp  when the value was read by StatusServer
     * @param value          value
     * @param writeTimestamp when the value was written on the remote server
     */
    public final void addValue(Timestamp readTimestamp, Value<? super T> value, Timestamp writeTimestamp) {
        AttributeValue<T> valueToAdd = AttributeValueFactory.newAttributeValue(this.name.getFullName(), this.name.getAlias(), value, readTimestamp, writeTimestamp);
        if (addValueInternal(valueToAdd)) {
            lastValue.set(valueToAdd);
            values.put(readTimestamp, valueToAdd);
            size.incrementAndGet();
        }
    }

    protected abstract boolean addValueInternal(AttributeValue<T> value);

    /**
     * Replaces lastValue does not change inMem values.
     *
     * @param readTimestamp
     * @param value
     * @param writeTimestamp
     */
    public void replaceValue(Timestamp readTimestamp, Value<? super T> value, Timestamp writeTimestamp) {
        lastValue.set(new AttributeValue<T>(this.name.getFullName(), this.name.getAlias(), value, readTimestamp, writeTimestamp));
    }

    /**
     * Returns the latest stored value.
     * <p/>
     * Performance comparable to O(log n) due to underlying SkipList.
     *
     * @return the latest value
     */
    public AttributeValue<T> getAttributeValue() {
        return lastValue.get();
    }

    public AttributeValue<T> getAttributeValue(Timestamp timestamp) {
        return getAttributeValue(timestamp, interpolation);
    }

    /**
     * Returns a view of all values stored after the timestamp.
     *
     * @param timestamp
     * @return
     */
    public Iterable<AttributeValue<T>> getAttributeValues(final Timestamp timestamp) {
        return values.tailMap(timestamp, true).values();
    }

    /**
     * @param timestamp
     * @param interpolation overrides default interpolation
     * @return
     */
    @SuppressWarnings("unchecked")
    public AttributeValue<T> getAttributeValue(Timestamp timestamp, Interpolation interpolation) {
        if (values.isEmpty()) {
            return lastValue.get();
        }

        Map.Entry<Timestamp, AttributeValue<T>> floorEntry = values.floorEntry(timestamp);
        if (floorEntry == null)
            floorEntry = values.firstEntry();
        AttributeValue<T> left = floorEntry.getValue();

        Map.Entry<Timestamp, AttributeValue<T>> ceilingEntry = values.ceilingEntry(timestamp);
        if (ceilingEntry == null)
            ceilingEntry = values.lastEntry();
        AttributeValue<T> right = ceilingEntry.getValue();

        return interpolation.interpolate(
                left,
                right,
                timestamp);
    }

    public AttributeName getName() {
        return name;
    }

    public String getAlias() {
        return name.getAlias();
    }

    public String getDeviceName() {
        return name.getDeviceName();
    }

    public String getFullName() {
        return name.getFullName();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Attribute attribute = (Attribute) o;

        if (name != null ? !name.equals(attribute.name) : attribute.name != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name);
    }

    @Override
    public String toString() {
        return name.toString();
    }

    /**
     * For tests only
     *
     * @return
     */
    Iterable<AttributeValue<T>> getAttributeValues() {
        return getAttributeValues(new Timestamp(0L));
    }

    /**
     * Erases inMem data from this attribute simultaneously inMem data is persisted
     */
    public void clear() {
        values.clear();
    }

    public long size() {
        return size.get();
    }

    public void eraseHead(Timestamp timestamp) {
        ConcurrentNavigableMap<Timestamp, AttributeValue<T>> head = values.headMap(timestamp);
        size.addAndGet(-head.size());
        head.clear();
    }
}