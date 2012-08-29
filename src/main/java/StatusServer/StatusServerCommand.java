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

package StatusServer;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.Multimap;
import fr.esrf.Tango.DevFailed;
import fr.esrf.TangoDs.Command;
import org.apache.log4j.Logger;
import wpn.hdri.ss.data.AttributeValue;
import wpn.hdri.ss.data.Timestamp;
import wpn.hdri.ss.engine.AttributeFilters;
import wpn.hdri.ss.engine.Engine;
import wpn.hdri.tango.command.AbsCommand;
import wpn.hdri.tango.data.EnumDevState;
import wpn.hdri.tango.data.type.ScalarTangoDataTypes;
import wpn.hdri.tango.data.type.SpectrumTangoDataTypes;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Igor Khokhriakov <igor.khokhriakov@hzg.de>
 * @since 10.05.12
 */
public enum StatusServerCommand {
    ERASE_DATA(new AbsCommand<StatusServer, Void, Void>("eraseData",
            ScalarTangoDataTypes.VOID, ScalarTangoDataTypes.VOID,
            "void", "void") {
        @Override
        protected Void executeInternal(StatusServer instance, Void data, Logger log) throws DevFailed {
            instance.getEngine().clear();
            return null;
        }
    }),
    START_LIGHT_POLLING(new AbsCommand<StatusServer, Void, Void>("startLightPolling",
            ScalarTangoDataTypes.VOID, ScalarTangoDataTypes.VOID,
            "void", "void") {
        @Override
        protected Void executeInternal(StatusServer ss, Void data, Logger log) throws DevFailed {
            ss.getEngine().startLightPolling();
            return null;
        }
    }),
    START_COLLECT_DATA(new AbsCommand<StatusServer, Void, Void>("startCollectData",
            ScalarTangoDataTypes.VOID, ScalarTangoDataTypes.VOID,
            "void", "void") {
        @Override
        protected Void executeInternal(StatusServer ss, Void data, Logger log) throws DevFailed {
            ss.getEngine().start();
            ss.set_state(EnumDevState.RUNNING.toDevState());
            ss.set_status(EnumDevState.RUNNING.name());
            return null;
        }
    }),
    STOP_COLLECT_DATA(new AbsCommand<StatusServer, Void, Void>("stopCollectData",
            ScalarTangoDataTypes.VOID, ScalarTangoDataTypes.VOID,
            "void", "void") {
        @Override
        protected Void executeInternal(StatusServer ss, Void data, Logger log) throws DevFailed {
            ss.getEngine().stop();
            ss.set_state(EnumDevState.ON.toDevState());
            ss.set_status(EnumDevState.ON.name());
            return null;
        }
    }),
    REGISTER_CLIENT(new AbsCommand<StatusServer, Void, Integer>("registerClient",
            ScalarTangoDataTypes.VOID, ScalarTangoDataTypes.INT,
            "void", "client id") {
        private final AtomicInteger clientId = new AtomicInteger(1);

        @Override
        protected Integer executeInternal(StatusServer ss, Void data, Logger log) throws DevFailed {
            return clientId.incrementAndGet();
        }
    }),
    GET_DATA_UPDATES(new AbsCommand<StatusServer, String, String[]>("getDataUpdates",
            ScalarTangoDataTypes.STRING, SpectrumTangoDataTypes.STRING_ARR,
            "client id", "strings array") {
        private final ConcurrentMap<Integer, Timestamp> timestamps = new ConcurrentHashMap<Integer, Timestamp>();

        @Override
        protected String[] executeInternal(StatusServer ss, String strId, Logger log) throws DevFailed {
            Integer uuid = Integer.valueOf(strId);

            Engine engine = ss.getEngine();

            final Timestamp timestamp = new Timestamp(System.currentTimeMillis());
            final Timestamp oldTimestamp = timestamps.put(uuid, timestamp);
            Multimap<String, AttributeValue<?>> attributes = engine.getAllAttributeValues(oldTimestamp, AttributeFilters.none());

            String[] rv = Collections2.transform(attributes.asMap().entrySet(), new Function<Map.Entry<String, Collection<AttributeValue<?>>>, String>() {
                @Override
                public String apply(Map.Entry<String, Collection<AttributeValue<?>>> input) {
                    return StatusServerCommandHelper.attributeToString(input);
                }
            }).toArray(new String[attributes.keySet().size()]);
            return rv;
        }
    }),
    GET_DATA(new AbsCommand<StatusServer, Void, String[]>("getData",
            ScalarTangoDataTypes.VOID, SpectrumTangoDataTypes.STRING_ARR,
            "void", "strings array") {
        @Override
        protected String[] executeInternal(StatusServer ss, Void data_in, Logger log) throws DevFailed {
            Engine engine = ss.getEngine();

            Multimap<String, AttributeValue<?>> attributes = engine.getAllAttributeValues(null, AttributeFilters.none());

            String[] rv = Collections2.transform(attributes.asMap().entrySet(), new Function<Map.Entry<String, Collection<AttributeValue<?>>>, String>() {
                @Override
                public String apply(Map.Entry<String, Collection<AttributeValue<?>>> input) {
                    return StatusServerCommandHelper.attributeToString(input);
                }
            }).toArray(new String[attributes.keySet().size()]);
            return rv;
        }
    }),
    GET_LATEST_SNAPSHOT(new AbsCommand<StatusServer, Void, String[]>("getLatestSnapshot",
            ScalarTangoDataTypes.VOID, SpectrumTangoDataTypes.STRING_ARR, "", "STRARR:\n element := attr_name=\nread_timestamp[value@write_timestamp]") {
        @Override
        public String[] executeInternal(StatusServer dev, Void data_in, Logger log) throws DevFailed {
            Engine engine = dev.getEngine();

            Collection<AttributeValue<?>> values = engine.getValues(Timestamp.now(), AttributeFilters.none());

            String[] output = StatusServerCommandHelper.printValues(values);
            return output;
        }
    }),
    GET_LATEST_SNAPSHOT_BY_GROUP(new AbsCommand<StatusServer, String, String[]>("getLatestSnapshotByGroup",
            ScalarTangoDataTypes.STRING, SpectrumTangoDataTypes.STRING_ARR, "group name", "STRARR:\n element := attr_name=\n read_timestamp[value@write_timestamp]") {
        @Override
        public String[] executeInternal(StatusServer dev, String data_in, Logger log) throws DevFailed {
            Engine engine = dev.getEngine();

            Collection<AttributeValue<?>> values = engine.getValues(Timestamp.now(), AttributeFilters.byGroup(data_in));

            String[] output = StatusServerCommandHelper.printValues(values);
            return output;
        }
    }),
    GET_SNAPSHOT(new AbsCommand<StatusServer, String, String[]>("getSnapshot",
            ScalarTangoDataTypes.STRING, SpectrumTangoDataTypes.STRING_ARR,
            "timestamp in milliseconds.", "STRARR:\n element := attr_name=\n read_timestamp[value@write_timestamp]"
    ) {
        @Override
        public String[] executeInternal(StatusServer ss, String data_in, Logger log) throws DevFailed {
            Engine engine = ss.getEngine();
            long value = Long.parseLong(data_in);
            Timestamp timestamp = new Timestamp(value);
            Collection<AttributeValue<?>> values = engine.getValues(timestamp, AttributeFilters.none());

            String[] output = StatusServerCommandHelper.printValues(values);
            return output;
        }
    }),
    GET_SNAPSHOT_BY_GROUP(new AbsCommand<StatusServer, String[], String[]>("getSnapshotByGroup",
            SpectrumTangoDataTypes.STRING_ARR, SpectrumTangoDataTypes.STRING_ARR,
            "arr: timestamp in milliseconds, group name.", "STRARR:\n element := attr_name=\n read_timestamp[value@write_timestamp]"
    ) {
        @Override
        public String[] executeInternal(StatusServer ss, String[] data_in, Logger log) throws DevFailed {
            Engine engine = ss.getEngine();

            long value = Long.parseLong(data_in[0]);
            String groupName = data_in[1];

            Timestamp timestamp = new Timestamp(value);
            Collection<AttributeValue<?>> values = engine.getValues(timestamp, AttributeFilters.byGroup(groupName));

            String[] output = StatusServerCommandHelper.printValues(values);
            return output;
        }
    }),
    CREATE_ATTRIBUTES_GROUP(new AbsCommand<StatusServer, String[], Void>("createAttributesGroup",
            SpectrumTangoDataTypes.STRING_ARR, ScalarTangoDataTypes.VOID, "group name", "") {
        @Override
        public Void executeInternal(StatusServer dev, String[] data_in, Logger log) throws DevFailed {
            Engine engine = dev.getEngine();

            engine.createAttributesGroup(data_in[0], Arrays.asList(Arrays.copyOfRange(data_in, 1, data_in.length)));

            return null;
        }
    });

    private final Command cmd;


    private StatusServerCommand(Command cmd) {
        this.cmd = cmd;
    }

    public Command toCommand() {
        return cmd;
    }
}