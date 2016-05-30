package com.bendyworks;

import java.util.Iterator;
import java.util.Map;

import clojure.lang.APersistentMap;
import clojure.lang.ASeq;
import clojure.lang.ATransientMap;
import clojure.lang.IEditableCollection;
import clojure.lang.IFn;
import clojure.lang.IMapEntry;
import clojure.lang.IPersistentCollection;
import clojure.lang.IPersistentMap;
import clojure.lang.IPersistentVector;
import clojure.lang.ISeq;
import clojure.lang.ITransientCollection;
import clojure.lang.ITransientMap;
import clojure.lang.MapEntry;
import clojure.lang.Obj;
import clojure.lang.RT;
import clojure.lang.SeqIterator;
import clojure.lang.Util;

public class LeanMap {
    final public static LeanMap EMPTY = new LeanMap();
}