/*
 * Copyright 2015 Odnoklassniki Ltd, Mail.Ru Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package one.nio.serial;

import one.nio.serial.gen.StubGenerator;

import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

public class EnumSerializer extends Serializer<Enum> {
    static final AtomicInteger enumCountMismatches = new AtomicInteger();
    static final AtomicInteger enumMissedConstants = new AtomicInteger();

    private Enum[] values;

    EnumSerializer(Class cls) {
        super(cls);
        this.values = cls().getEnumConstants();
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);
        Enum[] ownValues = cls().getEnumConstants();
        out.writeShort(ownValues.length);
        for (Enum v : ownValues) {
            out.writeUTF(v.name());
        }
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        String[] constants = new String[in.readUnsignedShort()];
        for (int i = 0; i < constants.length; i++) {
            constants[i] = in.readUTF();
        }

        try {
            super.readExternal(in);
        } catch (ClassNotFoundException e) {
            if ((Repository.getOptions() & Repository.ENUM_STUBS) == 0) throw e;
            this.cls = StubGenerator.generateEnum(uniqueName("Enum"), constants);
            this.origin = Origin.GENERATED;
        }

        Enum[] ownValues = cls().getEnumConstants();
        if (ownValues.length != constants.length) {
            Repository.log.warn("[" + Long.toHexString(uid) + "] Enum count mismatch for " + descriptor + ": " +
                    ownValues.length + " local vs. " + constants.length + " stream constants");
            enumCountMismatches.incrementAndGet();
        }

        this.values = new Enum[constants.length];
        for (int i = 0; i < constants.length; i++) {
            values[i] = find(ownValues, constants[i], i);
        }
    }

    public void skipExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        int constants = in.readUnsignedShort();
        for (int i = 0; i < constants; i++) {
            in.skipBytes(in.readUnsignedShort());
        }
    }

    @Override
    public void calcSize(Enum obj, CalcSizeStream css) {
        css.count += 2;
    }

    @Override
    public void write(Enum obj, DataStream out) throws IOException {
        out.writeShort(obj.ordinal());
    }

    @Override
    public Enum read(DataStream in) throws IOException {
        Enum result = values[in.readUnsignedShort()];
        in.register(result);
        return result;
    }

    @Override
    public void skip(DataStream in) throws IOException {
        in.skipBytes(2);
    }

    @Override
    public void toJson(Enum obj, StringBuilder builder) {
        builder.append('"').append(obj.name()).append('"');
    }

    private Enum find(Enum[] values, String name, int index) {
        for (Enum v : values) {
            if (name.equals(v.name())) {
                return v;
            }
        }

        Repository.log.warn("[" + Long.toHexString(uid) + "] Missed local enum constant " + descriptor + "." + name);
        enumMissedConstants.incrementAndGet();

        Default defaultName = cls().getAnnotation(Default.class);
        if (defaultName != null) {
            return Enum.valueOf(cls, defaultName.value());
        }
        return index < values.length ? values[index] : null;
    }
}
