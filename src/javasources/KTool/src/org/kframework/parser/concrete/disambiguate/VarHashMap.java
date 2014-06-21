// Copyright (c) 2013-2014 K Team. All Rights Reserved.
package org.kframework.parser.concrete.disambiguate;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class VarHashMap extends HashMap<String, Set<String>> {

    private static final long serialVersionUID = 1L;

    public int hashCode() {
        int code = 0;
        for (Map.Entry<String, Set<String>> entry : this.entrySet()) {
            code += entry.getKey().hashCode();
            for (String s : entry.getValue())
                code += s.hashCode();
        }
        return code;
    }

    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        if (obj == this)
            return true;
        if (obj instanceof VarHashMap) {
            VarHashMap vhm = (VarHashMap) obj;
            if (this.size() != vhm.size())
                return false;
            for (Map.Entry<String, Set<String>> entry : this.entrySet()) {
                if (!vhm.containsKey(entry.getKey()))
                    return false;
                Set<String> target = vhm.get(entry.getKey());
                if (entry.getValue().size() != target.size())
                    return false;
                for (String s : entry.getValue())
                    if (!target.contains(s))
                        return false;
            }
        } else
            return false;
        return true;
    }

    /**
     * For the given key, adds the value into a set. If the key doesn't exist, it creates the set.
     * @param key
     * @param value
     */
    public void add(String key, String value) {
        if (this.containsKey(key))
            this.get(key).add(value);
        else {
            java.util.Set<String> varss = new HashSet<>();
            varss.add(value);
            this.put(key, varss);
        }
    }

    /**
     * For the given key, adds all the values to a set. If the key doesn't exist, it creates the set.
     * @param key
     * @param value
     */
    public void addAll(String key, Set<String> value) {
        if (this.containsKey(key))
            this.get(key).addAll(value);
        else {
            java.util.Set<String> varss = new HashSet<>();
            varss.addAll(value);
            this.put(key, varss);
        }
    }
}
