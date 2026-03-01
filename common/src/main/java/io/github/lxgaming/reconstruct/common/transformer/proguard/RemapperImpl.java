/*
 * Copyright 2020 Alex Thomson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.lxgaming.reconstruct.common.transformer.proguard;

import io.github.lxgaming.reconstruct.common.Reconstruct;
import io.github.lxgaming.reconstruct.common.bytecode.Attribute;
import io.github.lxgaming.reconstruct.common.bytecode.Attributes;
import io.github.lxgaming.reconstruct.common.bytecode.RcClass;
import io.github.lxgaming.reconstruct.common.bytecode.RcField;
import io.github.lxgaming.reconstruct.common.bytecode.RcMethod;
import io.github.lxgaming.reconstruct.common.util.Toolbox;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.Remapper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class RemapperImpl extends Remapper {

    private final Map<String, RcClass> cachedClasses;
    private final Set<String> missingClasses;

    public RemapperImpl() {
        this.cachedClasses = new HashMap<>();
        this.missingClasses = new HashSet<>();
    }

    @Override
    public Object mapValue(Object value) {
        if (value instanceof Handle) {
            // https://gitlab.ow2.org/asm/asm/-/merge_requests/327
            Handle handle = (Handle) value;
            boolean isFieldHandle = handle.getTag() <= Opcodes.H_PUTSTATIC;

            return new Handle(
                handle.getTag(),
                mapType(handle.getOwner()),
                isFieldHandle
                    ? mapFieldName(handle.getOwner(), handle.getName(), handle.getDesc())
                    : mapMethodName(handle.getOwner(), handle.getName(), handle.getDesc()),
                isFieldHandle ? mapDesc(handle.getDesc()) : mapMethodDesc(handle.getDesc()),
                handle.isInterface());
        }

        return super.mapValue(value);
    }

    @Override
    public String mapMethodName(String owner, String name, String descriptor) {
        RcClass currentClass = getClass(Toolbox.toJavaName(owner), Attributes.OBFUSCATED_NAME);
        if (currentClass == null) {
            return name;
        }

        RcMethod currentMethod = getMethod(currentClass, name + descriptor, Attributes.OBFUSCATED_DESCRIPTOR);
        if (currentMethod == null) {
            return name;
        }

        Reconstruct.getInstance().getLogger().trace("Method {}.{}{} -> {}.{}{}", owner, name, descriptor, owner, currentMethod.getName(), descriptor);
        return currentMethod.getName();
    }

    @Override
    public String mapFieldName(String owner, String name, String descriptor) {
        RcClass currentClass = getClass(Toolbox.toJavaName(owner), Attributes.OBFUSCATED_NAME);
        if (currentClass == null) {
            return name;
        }

        RcField currentField = getField(currentClass, name + ":" + descriptor, Attributes.OBFUSCATED_DESCRIPTOR);
        if (currentField == null) {
            return name;
        }

        Reconstruct.getInstance().getLogger().trace("Field {}.{}:{} -> {}.{}:{}", owner, name, descriptor, owner, currentField.getName(), descriptor);
        return currentField.getName();
    }

    @Override
    public String map(String internalName) {
        RcClass currentClass = getClass(Toolbox.toJavaName(internalName), Attributes.OBFUSCATED_NAME);
        if (currentClass == null) {
            return internalName;
        }

        return Toolbox.toJvmName(currentClass.getName());
    }

    public RcClass getClass(String name, Attribute.Key<String> attribute) {
        if (attribute.equals(Attributes.OBFUSCATED_NAME)) {
            RcClass cachedClass = cachedClasses.get(name);
            if (cachedClass != null) {
                return cachedClass;
            }

            if (missingClasses.contains(name)) {
                return null;
            }
        }

        RcClass currentClass = Reconstruct.getInstance().getClass(name, attribute).orElse(null);
        if (currentClass != null) {
            if (attribute.equals(Attributes.OBFUSCATED_NAME)) {
                cachedClasses.put(name, currentClass);
            }

            Reconstruct.getInstance().getLogger().trace("Class {} -> {}", name, currentClass.getName());
            return currentClass;
        }

        if (attribute.equals(Attributes.OBFUSCATED_NAME)) {
            missingClasses.add(name);
        }

        return null;
    }

    private RcField getField(RcClass rcClass, String descriptor, Attribute.Key<String> attribute) {
        return rcClass.getField(field -> {
            return field.getAttribute(attribute).map(alternativeDescriptor -> {
                return alternativeDescriptor.equals(descriptor);
            }).orElse(false);
        });
    }

    private RcMethod getMethod(RcClass rcClass, String descriptor, Attribute.Key<String> attribute) {
        return rcClass.getMethod(method -> {
            return method.getAttribute(attribute).map(alternativeDescriptor -> {
                return alternativeDescriptor.equals(descriptor);
            }).orElse(false);
        });
    }
}
