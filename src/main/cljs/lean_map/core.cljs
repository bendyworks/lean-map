(ns cljs.lean-map.core
  (:refer-clojure :exclude [Box ->Box BitmapIndexedNode ->BitmapIndexedNode
                            HashCollisionNode ->HashCollisionNode
                            PersistentHashMap ->PersistentHashMap
                            TransientHashMap ->TransientHashMap
                            NodeSeq ->NodeSeq
                            create-inode-seq create-array-node-seq create-node
                            mask bitmap-indexed-node-index bitpos inode-kv-reduce]))

(declare NodeSeq HashCollisionNode TransientHashMap PersistentHashMap)

(deftype Box [^:mutable val])

(declare create-inode-seq create-array-node-seq create-node)

(defn- mask [hash shift]
  (bit-and (bit-shift-right-zero-fill hash shift) 0x01f))

(defn- bitmap-indexed-node-index [bitmap bit]
  (bit-count (bit-and bitmap (dec bit))))

(defn- bitpos [hash shift]
  (bit-shift-left 1 (mask hash shift)))

(defn- can-edit [x y]
  (and (coercive-not= x nil) (coercive-not= y nil) (identical? x y)))

(defn- inode-kv-reduce [arr kvs nodes f init]
  (let [kv-len (* 2 kvs)
        node-len (+ kv-len nodes)]
    (loop [i 0 init init]
      (cond (< i kv-len)
            (let [init (f init (aget arr i) (aget arr (inc i)))]
              (if (reduced? init)
                @init
                (recur (+ i 2) init)))
            (< i node-len)
            (let [init (.kv-reduce (aget arr i) f init)]
              (if (reduced? init)
                @init
                (recur (inc i) init)))
            :else init))))

(defn- ^number hash-kv [key value]
  (let [key-hash-code (bit-or (+ 31 (hash key)) 0)]
    (mix-collection-hash (bit-or (+ (imul 31 key-hash-code) (hash value)) 0) 2)))

(deftype KeyValue [key value]
  Object
  (to-vector [_]
    [key value])
  (new-val [_ val]
    (KeyValue. key val))
  IHash
  (-hash [_]
    (let [key-hash-code (bit-or (+ 31 (hash key)) 0)]
      (mix-collection-hash (bit-or (+ (imul 31 key-hash-code) (hash value)) 0) 2))))

(deftype BitmapIndexedNode [edit ^:mutable datamap ^:mutable nodemap ^:mutable arr]
  Object
  (node-at [_ bit]
    (- (alength arr) 1 (bitmap-indexed-node-index nodemap bit)))

  (copy-and-set-node [inode e bit node]
    (let [idx (.node-at inode bit)]
      (if ^boolean (can-edit e edit)
        (do
          (aset arr idx node)
          inode)
        (let [new-arr (aclone arr)]
          (aset new-arr idx node)
          (BitmapIndexedNode. e datamap nodemap new-arr)))))

  (copy-and-set-value [inode e bit val]
    (let [idx (bitmap-indexed-node-index datamap bit)]
      (if ^boolean (can-edit e edit)
        (do
          (aset arr idx val)
          inode)
        (let [new-arr (aclone arr)]
          (aset new-arr idx val)
          (BitmapIndexedNode. e datamap nodemap new-arr)))))

  (copy-and-migrate-to-node [inode e bit node]
    (let [idx-old (bitmap-indexed-node-index datamap bit)
          idx-new (- (alength arr) 2 (bitmap-indexed-node-index nodemap bit))
          dst (make-array (alength arr))]
      (array-copy arr 0 dst 0 idx-old)
      (array-copy arr (inc idx-old) dst idx-old (- idx-new idx-old))
      (aset dst idx-new node)
      (array-copy arr (inc idx-new) dst (inc idx-new) (- (alength arr) idx-new 1))
      (BitmapIndexedNode. e (bit-xor datamap bit) (bit-or nodemap bit) dst)))

  (merge-two-kv-pairs [inode medit shift keyval1 key2hash keyval2]
    (let [key1hash (hash (.-key keyval1))]
      (if (and (< 32 shift) (== key1hash key2hash))
        (HashCollisionNode. medit key1hash 2 (array keyval1 keyval2))
        (let [mask1 (mask key1hash shift)
              mask2 (mask key2hash shift)]
          (if (== mask1 mask2)
            (let [new-node (.merge-two-kv-pairs inode medit (+ shift 5) keyval1 key2hash keyval2)]
              (BitmapIndexedNode. medit 0 (bitpos key1hash shift) (array new-node)))
            (let [new-datamap (bit-or (bitpos key1hash shift) (bitpos key2hash shift))]
              (if (< mask1 mask2)
                (BitmapIndexedNode. medit new-datamap 0 (array keyval1 keyval2))
                (BitmapIndexedNode. medit new-datamap 0 (array keyval2 keyval1)))))))))

  (inode-seq [inode]
    (let [nodes (make-array 7)
          cursors-lengths #js [0 0 0 0 0 0 0]]
      (aset nodes 0 inode)
      (aset cursors-lengths 0 (.node-arity inode))
      (if (zero? datamap)
        (create-inode-seq arr 0 nodes cursors-lengths 0 0)
        (NodeSeq. nil arr 0 nodes cursors-lengths 0 (dec (.data-arity inode)) nil))))

  (inode-assoc [inode aedit shift hash key val added-leaf?]
    (let [bit (bitpos hash shift)]
      (cond
        (not (zero? (bit-and datamap bit)))
        (let [idx (bitmap-indexed-node-index datamap bit)
              kv (aget arr idx)]
          (if (key-test (.-key kv) key)
            (.copy-and-set-value inode aedit bit (.new-value kv val))
            (let [new-node (.merge-two-kv-pairs inode aedit (+ shift 5) kv hash (KeyValue. key val))]
              (set! (.-val added-leaf?) true)
              (.copy-and-migrate-to-node inode aedit bit new-node))))
        (not (zero? (bit-and nodemap bit)))
        (let [sub-node (aget arr (.node-at inode bit))
              sub-node-new (.inode-assoc sub-node aedit (+ shift 5) hash key val added-leaf?)]
          (if (identical? sub-node sub-node-new)
            inode
            (.copy-and-set-node inode aedit bit sub-node-new)))
        :else
        (let [n (alength arr)
              idx (bitmap-indexed-node-index datamap bit)
              new-arr (make-array (inc n))]
          (array-copy arr 0 new-arr 0 idx )
          (aset new-arr idx (KeyValue. key val))
          (array-copy arr idx new-arr (inc idx) (- n idx))
          (set! (.-val added-leaf?) true)
          (BitmapIndexedNode. aedit (bit-or datamap bit) nodemap new-arr)))))

  (has-nodes? [_]
    (not (zero? nodemap)))

  (node-arity [_]
    (bit-count nodemap))

  (has-data? [_]
    (not (zero? datamap)))

  (data-arity [_]
    (bit-count datamap))

  (get-node [_ i]
    (aget arr (- (alength arr) i)))

  (get-array [_]
    arr)

  (inode-lookup [inode shift hash key not-found]
    (let [bit (bitpos hash shift)]
      (cond
        (not (zero? (bit-and datamap bit)))
        (let [idx (bitmap-indexed-node-index datamap bit)
              kv (aget arr idx)]
          (if (key-test (.-key kv) key)
            (.-value kv)
            not-found))
        (not (zero? (bit-and nodemap bit)))
        (.inode-lookup (aget arr (.node-at inode bit)) (+ shift 5) hash key not-found)
        :else
        not-found)))

  (inode-find [inode shift hash key not-found]
    (let [bit (bitpos hash shift)]
      (cond
        (not (zero? (bit-and datamap bit)))
        (let [idx (bitmap-indexed-node-index datamap bit)
              kv (aget arr idx)]
          (if (key-test (.-key kv) key)
            (.-to-vector kv)
            not-found))
        (not (zero? (bit-and nodemap bit)))
        (.inode-lookup (.node-at inode bit) (+ shift 5) hash key not-found)
        :else
        not-found)))

  (copy-and-remove-value [indoe e bit]
    (let [idx (bitmap-indexed-node-index datamap bit)
          len (alength arr)
          dst (make-array (dec len))]
      (array-copy arr 0 dst 0 idx)
      (array-copy arr (dec idx) dst idx (- len idx 1))
      (BitmapIndexedNode. e (bit-xor datamap bit) nodemap dst)))

  (copy-and-migrate-to-inline [inode e bit node]
    (let [idx-old (- (alength arr) 1 (bitmap-indexed-node-index nodemap bit))
          idx-new (bitmap-indexed-node-index datamap bit)
          dst (make-array (alength arr))]
      (array-copy arr 0 dst 0 idx-new)
      (aset dst idx-new (aget (.-arr node) 0))
      (array-copy arr idx-new dst (inc idx-new) (- idx-old idx-new))
      (array-copy arr (inc idx-old) dst (inc idx-old) (- (alength arr) idx-old 1))
      (BitmapIndexedNode. e (bit-or datamap bit) (bit-xor nodemap bit) dst)))

  (kv-reduce [inode f init]
    (inode-kv-reduce arr (bit-count datamap) (bit-count nodemap) f init))

  (single-kv? [_]
    (and (zero? nodemap) (== 1 (bit-count datamap))))

  (inode-without [inode wedit shift hash key removed-leaf?]
    (let [bit (bitpos hash shift)]
      (cond
        (not (zero? (bit-and datamap bit)))
        (let [idx (bitmap-indexed-node-index datamap bit)
              kv (aget arr idx)]
          (if (key-test key (.-key kv))
            (do
              (set! (.-val removed-leaf?) true)
              (if (and (== 2 (bit-count datamap)) (zero? nodemap))
                (let [new-datamap (if (zero? shift) (bit-xor datamap bit) (bitpos hash 0))]
                  (BitmapIndexedNode. wedit new-datamap 0 (array kv)))
               (.copy-and-remove-value inode wedit bit)))
            inode))
        (not (zero? (bit-and nodemap bit)))
        (let [sub-node (aget arr (.node-at inode bit))
              sub-node-new (.inode-without sub-node wedit (+ shift 5) hash key removed-leaf?)]
          (if (identical? sub-node sub-node-new)
            inode
            (if ^boolean (.single-kv? sub-node-new)
              (if (and (zero? datamap) (== 1 (bit-count nodemap)))
                sub-node-new
                (.copy-and-migrate-to-inline inode wedit bit sub-node-new))
              (.copy-and-set-node inode wedit bit sub-node-new))))
        :else
        inode)))

  (hash-node [_ hash-code]
    (let [data-len (* (bit-count datamap) 2)
          len (alength arr)
          node-start (if (zero? datamap) 0 data-len)]
      (loop [d 0 hash-code hash-code]
        (if (< d data-len)
          (recur (inc d) (bit-or (+ hash-code (-hash (aget arr d))) 0))
          (loop [n node-start hash-code hash-code]
            (if (< n len)
              (recur (inc n) (.hash-node (aget arr n) hash-code))
              hash-code))))))

  IEquiv
  (-equiv [inode other]
    (if (identical? inode other)
      true
      (when (instance? BitmapIndexedNode other)
        (when (and (== datamap (.-datamap other)) (== nodemap (.-nodemap other)))
          (let [len (alength arr)]
            (loop [i 0 eq true]
              (if (and eq (< i len))
                (recur (inc i) (= (aget arr i) (aget (.-arr other)i)))
                eq))))))))

(set! (.-EMPTY BitmapIndexedNode) (BitmapIndexedNode. nil 0 0 (make-array 0)))

(deftype HashCollisionNode [edit
                            ^:mutable collision-hash
                            ^:mutable cnt
                            ^:mutable arr]
  Object
  (persistent-inode-assoc [inode idx hedit key val added-leaf?]
    (if (== idx -1)
      (let [len     (* 2 cnt)
            new-arr (make-array (+ len 2))]
        (array-copy arr 0 new-arr 0 len)
        (aset new-arr len key)
        (aset new-arr (inc len) val)
        (set! (.-val added-leaf?) true)
        (HashCollisionNode. hedit collision-hash (inc cnt) new-arr))
      (if (= (aget arr idx) val)
        inode
        (HashCollisionNode. hedit collision-hash cnt (clone-and-set arr (inc idx) val)))))

  (mutable-inode-assoc [inode idx key val added-leaf?]
    (if (== idx -1)
      (let [new-arr (make-array (* 2 (inc cnt)))]
        (array-copy arr 0 new-arr 0 (* 2 cnt))
        (aset arr (* 2 cnt) key)
        (aset arr (inc (* 2 cnt)) val)
        (set! (.-val added-leaf?) true)
        (set! cnt (inc cnt)))
      (when-not (identical? (aget arr (inc idx)) val)
        (aset arr (inc idx) val)))
    inode)

  (inode-assoc [inode hedit _ hash key val added-leaf?]
    (assert (== hash collision-hash))
    (let [idx (hash-collision-node-find-index arr cnt key)]
      (if ^boolean hedit
        (if ^boolean (can-edit edit hedit)
         (.mutable-inode-assoc inode idx key val added-leaf?)
         (.mutable-inode-assoc (HashCollisionNode. hedit hash cnt (aclone arr)) idx key val added-leaf?))
        (.persistent-inode-assoc inode idx hedit key val added-leaf?))))

  (has-nodes? [_]
    false)

  (node-arity [_]
    0)

  (has-data? [_]
    true)

  (data-arity [_]
    cnt)

  (get-node [_ _]
    nil)

  (get-array [_]
    arr)

  (inode-lookup [inode shift hash key not-found]
    (let [idx (hash-collision-node-find-index arr cnt key)]
      (cond (< idx 0)              not-found
            (key-test key (aget arr idx)) (aget arr (inc idx))
            :else                  not-found)))

  (inode-find [inode shift hash key not-found]
    (let [idx (hash-collision-node-find-index arr cnt key)]
      (cond (< idx 0)              not-found
            (key-test key (aget arr idx)) [(aget arr idx) (aget arr (inc idx))]
            :else                  not-found)))

  (inode-without [inode wedit shift hash key removed-leaf?]
    (let [idx (hash-collision-node-find-index arr cnt key)]
      (if (== idx -1)
        inode
        (do
          (set! (.-val removed-leaf?) true)
          (case cnt
            1
            (.-EMPTY BitmapIndexedNode)
            2
            (let [idx (if (key-test key (aget arr 0)) 2 0)]
              (.inode-assoc (.-EMPTY BitmapIndexedNode) wedit 0 hash (aget arr idx) (aget arr (inc idx)) removed-leaf?))
            (HashCollisionNode. wedit collision-hash (dec cnt) (remove-pair arr (quot idx 2))))))))

  (kv-reduce [inode f init]
    (inode-kv-reduce arr cnt 0 f init))

  (single-kv? [_]
    (== 1 cnt))

  (hash-node [_ hash-code]
    (let [len (alength arr)]
      (loop [n 0 hash-code hash-code]
        (if (< n len)
          (recur (+ n 2) (bit-or (+ hash-code (hash [(aget arr n) (aget arr (inc n))])) 0))
          hash-code))))

  IEquiv
  (-equiv [inode other]
    (if (identical? inode other)
      true
      (when (instance? HashCollisionNode other)
        (when (== cnt (.-cnt other))
          (let [len (alength arr)
                other-arr (.-arr other)]
            (loop [i 0 eq true]
              (if (and eq (< i len))
                (let [idx (hash-collision-node-find-index other-arr cnt (aget arr i))]
                  (recur (+ i 2)
                         (and (> idx -1) (= (aget arr (inc i)) (aget other-arr (inc idx))))))
                eq))))))))

(defn- persistent-map-hash [m]
  (mix-collection-hash (if (zero? (.-cnt m)) 0 (.hash-node (.-root m) 0)) (.-cnt m)))

(deftype PersistentHashMap [meta cnt root ^:mutable __hash]
  Object
  (toString [coll]
    (pr-str* coll))
  (equiv [this other]
    (-equiv this other))

  ;; EXPERIMENTAL: subject to change
  (keys [coll]
    (es6-iterator (keys coll)))
  (entries [coll]
    (es6-entries-iterator (seq coll)))
  (values [coll]
    (es6-iterator (vals coll)))
  (has [coll k]
    (contains? coll k))
  (get [coll k not-found]
    (-lookup coll k not-found))
  (forEach [coll f]
    (doseq [[k v] coll]
      (f v k)))

  ICloneable
  (-clone [_] (PersistentHashMap. meta cnt root __hash))

  IWithMeta
  (-with-meta [coll meta] (PersistentHashMap. meta cnt root  __hash))

  IMeta
  (-meta [coll] meta)

  ICollection
  (-conj [coll entry]
    (if (vector? entry)
      (-assoc coll (-nth entry 0) (-nth entry 1))
      (loop [ret coll es (seq entry)]
        (if (nil? es)
          ret
          (let [e (first es)]
            (if (vector? e)
              (recur (-assoc ret (-nth e 0) (-nth e 1))
                     (next es))
              (throw (js/Error. "conj on a map takes map entries or seqables of map entries"))))))))


  IEmptyableCollection
  (-empty [coll] (-with-meta (.-EMPTY PersistentHashMap) meta))

  IEquiv
  (-equiv [coll other]
    (if (identical? coll other)
      true
      (if (instance? PersistentHashMap other)
       (-equiv root (.-root other))
       (equiv-map coll other))))

  IHash
  (-hash [coll]
    (caching-hash coll persistent-map-hash __hash))

  ILookup
  (-lookup [coll k]
    (-lookup coll k nil))

  (-lookup [coll k not-found]
    (if (nil? root)
      not-found (.inode-lookup root 0 (hash k) k not-found)))

  IFn
  (-invoke [coll k]
    (-lookup coll k))

  (-invoke [coll k not-found]
    (-lookup coll k not-found))

  ISeqable
  (-seq [coll]
    (when (pos? cnt)
      (.inode-seq root)))

  ICounted
  (-count [coll] cnt)

  IAssociative
  (-assoc [coll k v]
    (let [added-leaf? (Box. false)
          new-root (.inode-assoc root nil 0 (hash k) k v added-leaf?)]
      (if (identical? new-root root)
        coll
        (PersistentHashMap. meta (if ^boolean (.-val added-leaf?) (inc cnt) cnt) new-root  nil))))

  (-contains-key? [coll k]
    (if (nil? root)
      false
      (not (identical? (.inode-lookup root 0 (hash k) k lookup-sentinel) lookup-sentinel))))

  IEditableCollection
  (-as-transient [coll]
    (TransientHashMap. (js-obj) root cnt))

  IMap
  (-dissoc [coll k]
    (if (nil? root)
      coll
      (let [new-root (.inode-without root nil 0 (hash k) k (Box. false))]
        (if (identical? new-root root)
          coll
          (PersistentHashMap. meta (dec cnt) new-root nil)))))

  IKVReduce
  (-kv-reduce [coll f init]
    (cond
      (reduced? init)          @init
      (not (nil? root)) (.kv-reduce root f init)
      :else                    init)))

(set! (.-EMPTY PersistentHashMap) (PersistentHashMap. nil 0 (.-EMPTY BitmapIndexedNode) empty-unordered-hash))

(deftype TransientHashMap [^:mutable ^boolean edit
                           ^:mutable root
                           ^:mutable count]
  Object
  (conj! [tcoll o]
    (if edit
      (if (satisfies? IMapEntry o)
        (.assoc! tcoll (key o) (val o))
        (loop [es (seq o) tcoll tcoll]
          (if-let [e ^boolean (first es)]
            (recur (next es)
                   (.assoc! tcoll (key e) (val e)))
            tcoll)))
      (throw (js/Error. "conj! after persistent"))))

  (assoc! [tcoll k v]
    (if edit
      (let [added-leaf? (Box. false)
            node (.inode-assoc root edit 0 (hash k) k v added-leaf?)]
        (if (identical? node root)
          nil
          (set! root node))
        (if ^boolean (.-val added-leaf?)
          (set! count (inc count)))
        tcoll)
      (throw (js/Error. "assoc! after persistent!"))))

  (without! [tcoll k]
    (if edit
      (if (nil? root)
        tcoll
        (let [removed-leaf? (Box. false)
              node (.inode-without root edit 0 (hash k) k removed-leaf?)]
          (if (identical? node root)
            nil
            (set! root node))
          (if ^boolean (.-val removed-leaf?)
            (set! count (dec count)))
          tcoll))
      (throw (js/Error. "dissoc! after persistent!"))))


  (persistent! [tcoll]
    (if edit
      (do (set! edit nil)
          (PersistentHashMap. nil count root nil))
      (throw (js/Error. "persistent! called twice"))))

  ICounted
  (-count [coll]
    (if edit
      count
      (throw (js/Error. "count after persistent!"))))

  ITransientCollection
  (-conj! [tcoll val] (.conj! tcoll val))

  (-persistent! [tcoll] (.persistent! tcoll))

  ITransientAssociative
  (-assoc! [tcoll key val] (.assoc! tcoll key val))

  ITransientMap
  (-dissoc! [tcoll key] (.without! tcoll key)))

(deftype NodeSeq [meta arr lvl nodes cursors data-idx data-len ^:mutable __hash]
  Object
  (toString [coll]
    (pr-str* coll))
  (equiv [this other]
    (-equiv this other))

  IMeta
  (-meta [coll] meta)

  IWithMeta
  (-with-meta [coll meta] (NodeSeq. meta arr lvl nodes cursors data-idx data-len __hash))

  ICollection
  (-conj [coll o] (cons o coll))

  IEmptyableCollection
  (-empty [coll] (with-meta (.-EMPTY List) meta))

  ISequential
  ISeq
  (-first [coll]
    [(aget arr (* data-idx 2)) (aget arr (inc (* data-idx 2)))])

  (-rest [coll]
    (create-inode-seq arr lvl nodes cursors data-idx data-len))

  ISeqable
  (-seq [this] this)

  IEquiv
  (-equiv [coll other] (equiv-sequential coll other))

  IHash
  (-hash [coll] (caching-hash coll hash-ordered-coll __hash))

  IReduce
  (-reduce [coll f] (seq-reduce f coll))
  (-reduce [coll f start] (seq-reduce f start coll)))

(defn node-arr-clone [arr]
  #js[(aget arr 0) (aget arr 1) (aget arr 2) (aget arr 3) (aget arr 4) (aget arr 5) (aget arr 6)])

(defn- create-inode-seq [arr lvl nodes cursors data-idx data-len]
  (if (< data-idx data-len)
    (NodeSeq. nil arr lvl nodes cursors (inc data-idx) data-len nil)
    (let [nodes     (node-arr-clone nodes)
          cursors (node-arr-clone cursors)]
      (loop [lvl lvl]
        (when (>= lvl 0)
          (let [node-idx (aget cursors lvl)]
            (if (zero? node-idx)
              (recur (dec lvl))
              (let [node (.get-node (aget nodes lvl) node-idx)
                    has-nodes ^boolean (.has-nodes? node)
                    new-lvl (if has-nodes (inc lvl) lvl)]
                (aset cursors lvl (dec node-idx))
                (when has-nodes
                  (aset nodes new-lvl node)
                  (aset cursors new-lvl (.node-arity node)))
                (if ^boolean (.has-data? node)
                  (NodeSeq. nil (.get-array node) new-lvl nodes cursors 0 (dec (.data-arity node)) nil)
                  (recur (inc lvl)))))))))))

(extend-protocol IPrintWithWriter
  NodeSeq
  (-pr-writer [coll writer opts] (pr-sequential-writer writer pr-writer "(" " " ")" opts coll))
  PersistentHashMap
  (-pr-writer [coll writer opts]
    (print-map coll pr-writer writer opts)))