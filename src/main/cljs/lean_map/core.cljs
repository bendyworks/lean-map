(ns cljs.lean-map.core
  (:refer-clojure :exclude [Box ->Box BitmapIndexedNode ->BitmapIndexedNode
                            HashCollisionNode ->HashCollisionNode
                            PersistentHashMap ->PersistentHashMap
                            TransientHashMap ->TransientHashMap
                            NodeSeq ->NodeSeq
                            create-inode-seq create-array-node-seq create-node
                            mask bitmap-indexed-node-index bitpos]))

(declare NodeSeq HashCollisionNode TransientHashMap PersistentHashMap)

(deftype Box [^:mutable added ^:mutable modified])

(declare create-inode-seq create-array-node-seq create-node)

(defn- mask [hash shift]
  (bit-and (bit-shift-right-zero-fill hash shift) 0x01f))

(defn- bitmap-indexed-node-index [bitmap bit]
  (bit-count (bit-and bitmap (dec bit))))

(defn- bitpos [hash shift]
  (bit-shift-left 1 (mask hash shift)))

(defn- node-at [arr nodemap bit]
  (- (alength arr) 1 (bitmap-indexed-node-index nodemap bit)))

(defn- can-edit [x y]
  (and (coercive-not= x nil) (coercive-not= y nil) (identical? x y)))

(deftype BitmapIndexedNode [edit ^:mutable datamap ^:mutable nodemap ^:mutable arr]
  Object
  (copy-and-set-node [inode e bit node]
    (let [idx (node-at arr nodemap bit)]
      (if (can-edit e edit)
        (do
          (aset arr idx node)
          inode)
        (let [new-arr (aclone arr)]
          (aset new-arr idx node)
          (BitmapIndexedNode. e datamap nodemap new-arr)))))

  (copy-and-set-value [inode e bit val]
    (let [idx (inc (* 2 (bitmap-indexed-node-index datamap bit)))]
      (if (can-edit e edit)
        (do
          (aset arr idx val)
          inode)
        (let [new-arr (aclone arr)]
          (aset new-arr idx val)
          (BitmapIndexedNode. e datamap nodemap new-arr)))))

  (copy-and-migrate-to-node [inode e bit node]
    (let [idx-old (* 2 (bitmap-indexed-node-index datamap bit))
          idx-new (- (alength arr) 2 (bitmap-indexed-node-index nodemap bit))
          dst (make-array (dec (alength arr)))]
      (array-copy arr 0 dst 0 idx-old)
      (array-copy arr (+ 2 idx-old) dst idx-old (- idx-new idx-old))
      (aset dst idx-new node)
      (array-copy arr (+ idx-new 2) dst (inc idx-new) (- (alength arr) idx-new 2))
      (BitmapIndexedNode. e (bit-xor datamap bit) (bit-or nodemap bit) dst)))

  (merge-two-kv-pairs [inode medit shift key1 val1 key2hash key2 val2]
    (let [key1hash (hash key1)]
      (if (and (< shift 32) (== key1hash key2hash))
        (HashCollisionNode. medit key1hash 2 (array key1 val1 key2 val2))
        (let [mask1 (mask key1hash shift)
              mask2 (mask key2hash shift)]
          (if (== mask1 mask2)
            (let [new-node (.merge-two-kv-pairs inode medit (+ shift 5) key1 val1 key2hash key2 val2)]
              (BitmapIndexedNode. medit 0 (bitpos key1hash shift) (array new-node)))
            (let [new-datamap (bit-or (bitpos key1hash shift) (bitpos key2hash shift))]
              (if (< mask1 mask2)
                (BitmapIndexedNode. medit new-datamap 0 (array key1 val1 key2 val2))
                (BitmapIndexedNode. medit new-datamap 0 (array key2 val2 key1 val1)))))))))

  (inode-seq [inode]
    (if (zero? datamap)
      (NodeSeq. nil arr -1 (dec (alength arr)) 0 (.inode-seq (aget arr (dec (alength arr)))) nil)
      (NodeSeq. nil arr (dec (* 2 (bit-count datamap))) (dec (alength arr)) 0 nil nil)))

  (inode-assoc [inode aedit shift hash key val changed?]
    (let [bit (bitpos hash shift)]
      (cond
        (not (zero? (bit-and datamap bit)))
        (let [idx (bitmap-indexed-node-index datamap bit)
              k (aget arr (* 2 idx))]
          (set! (.-modified changed?) true)
          (if (key-test k key)
            (.copy-and-set-value inode aedit bit val)
            (let [v (aget arr (inc (* 2 idx)))
                  new-node (.merge-two-kv-pairs inode aedit (+ shift 5) k v hash key val)]
              (set! (.-added changed?) true)
              (.copy-and-migrate-to-node inode aedit bit new-node))))
        (not (zero? (bit-and nodemap bit)))
        (let [sub-node (aget arr (node-at arr nodemap bit))
              sub-node-new (.inode-assoc sub-node aedit (+ shift 5) hash key val changed?)]
          (if (.-modified changed?)
            (.copy-and-set-node inode aedit bit sub-node-new)
            inode))
        :else
        (let [n (alength arr)
              idx (* 2 (bitmap-indexed-node-index datamap bit))
              new-arr (make-array (+ 2 n))]
          (array-copy arr 0 new-arr 0 idx )
          (aset new-arr idx key)
          (aset new-arr (inc idx) val)
          (array-copy arr idx new-arr (+ 2 idx) (- n idx))
          (set! (.-added changed?) true)
          (set! (.-modified changed?) true)
          (BitmapIndexedNode. aedit (bit-or datamap bit) nodemap new-arr)))))

  (inode-lookup [inode shift hash key not-found]
    (let [bit (bitpos hash shift)]
      (cond
        (not (zero? (bit-and datamap bit)))
        (let [idx (bitmap-indexed-node-index datamap bit)
              k (aget arr (* 2 idx))]
          (if (=  k key)
            (aget arr (inc (* 2 idx)))
            not-found))
        (not (zero? (bit-and nodemap bit)))
        (.inode-lookup (aget arr (node-at arr nodemap bit)) (+ shift 5) hash key not-found)
        :else
        not-found)))

  (inode-find [inode shift hash key not-found]
    (let [bit (bitpos hash shift)]
      (cond
        (not (zero? (bit-and datamap bit)))
        (let [idx (bitmap-indexed-node-index datamap bit)
              k (aget arr (* 2 idx))]
          (if (= k key)
            [k (aget arr (inc (* 2 idx)))]
            not-found))
        (not (zero? (bit-and nodemap bit)))
        (.inode-lookup (node-at arr nodemap bit) (+ shift 5) hash key not-found)
        :else
        not-found))))

(set! (.-EMPTY BitmapIndexedNode) (BitmapIndexedNode. nil 0 0 (make-array 0)))

(deftype HashCollisionNode [edit
                            ^:mutable collision-hash
                            ^:mutable cnt
                            ^:mutable arr]
  Object
  (inode-assoc [inode hedit shift hash key val added-leaf?]
    (assert (== hash collision-hash))
    (let [idx (hash-collision-node-find-index arr cnt key)]
      (if (== idx -1)
        (let [len     (* 2 cnt)
              new-arr (make-array (+ len 2))]
          (array-copy arr 0 new-arr 0 len)
          (aset new-arr len key)
          (aset new-arr (inc len) val)
          (set! (.-val added-leaf?) true)
          (HashCollisionNode. nil collision-hash (inc cnt) new-arr))
        (if (= (aget arr idx) val)
          inode
          (HashCollisionNode. nil collision-hash cnt (clone-and-set arr (inc idx) val))))))

  (inode-lookup [inode shift hash key not-found]
    (let [idx (hash-collision-node-find-index arr cnt key)]
      (cond (< idx 0)              not-found
            (key-test key (aget arr idx)) (aget arr (inc idx))
            :else                  not-found)))

  (inode-find [inode shift hash key not-found]
    (let [idx (hash-collision-node-find-index arr cnt key)]
      (cond (< idx 0)              not-found
            (key-test key (aget arr idx)) [(aget arr idx) (aget arr (inc idx))]
            :else                  not-found))))

(defn- create-node [edit shift key1 val1 key2hash key2 val2]
  (let [key1hash (hash key1)]
    (if (== key1hash key2hash)
      (HashCollisionNode. nil key1hash 2 (array key1 val1 key2 val2))
      (let [changed? (Box. false false)]
        (-> (.-EMPTY BitmapIndexedNode)
            (.inode-assoc edit shift key1hash key1 val1 changed?)
            (.inode-assoc edit shift key2hash key2 val2 changed?))))))

(deftype PersistentHashMap [meta cnt root ^:mutable __hash]
  Object
  (toString [coll]
    (pr-str* coll))
  (equiv [this other]
    (-equiv this other))

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
  (-equiv [coll other] (equiv-map coll other))

  IHash
  (-hash [coll] (caching-hash coll hash-unordered-coll __hash))

  ILookup
  (-lookup [coll k]
    (-lookup coll k nil))

  (-lookup [coll k not-found]
    (if (nil? root)
      not-found (.inode-lookup root 0 (hash k) k not-found)))


  ISeqable
  (-seq [coll]
    (when (pos? cnt)
      (.inode-seq root)))

  ICounted
  (-count [coll] cnt)

  IAssociative
  (-assoc [coll k v]
    (let [changed? (Box. false false)
          new-root    (-> (if (nil? root)
                            (.-EMPTY BitmapIndexedNode)
                            root)
                          (.inode-assoc nil 0 (hash k) k v changed?))]
      (if (identical? new-root root)
        coll
        (PersistentHashMap. meta (if ^boolean (.-added changed?) (inc cnt) cnt) new-root  nil))))

  (-contains-key? [coll k]
    (if (nil? root)
      false
      (not (identical? (.inode-lookup root 0 (hash k) k lookup-sentinel) lookup-sentinel))))

  IEditableCollection
  (-as-transient [coll]
    (TransientHashMap. (js-obj) root cnt)))

(set! (.-EMPTY PersistentHashMap) (PersistentHashMap. nil 0 nil empty-unordered-hash))

(deftype TransientHashMap [^:mutable ^boolean edit
                           ^:mutable root
                           ^:mutable count]
  Object
  (conj! [tcoll o]
    (if edit
      (if (satisfies? IMapEntry o)
        (.assoc! tcoll (key o) (val o))
        (loop [es (seq o) tcoll tcoll]
          (if-let [e (first es)]
            (recur (next es)
                   (.assoc! tcoll (key e) (val e)))
            tcoll)))
      (throw (js/Error. "conj! after persistent"))))

  (assoc! [tcoll k v]
    (if edit
      (let [changed? (Box. false false)
            node        (-> (if (nil? root)
                              (.-EMPTY BitmapIndexedNode)
                              root)
                            (.inode-assoc edit 0 (hash k) k v changed?))]
        (if (identical? node root)
          nil
          (set! root node))
        (if ^boolean (.-added changed?)
          (set! count (inc count)))
        tcoll)
      (throw (js/Error. "assoc! after persistent!"))))

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
  (-assoc! [tcoll key val] (.assoc! tcoll key val)))

(deftype NodeSeq [meta nodes dlen nloc i s ^:mutable __hash]
  Object
  (toString [coll]
    (pr-str* coll))
  (equiv [this other]
    (-equiv this other))

  IMeta
  (-meta [coll] meta)

  IWithMeta
  (-with-meta [coll meta] (NodeSeq. meta nodes dlen nloc i s __hash))

  ICollection
  (-conj [coll o] (cons o coll))

  IEmptyableCollection
  (-empty [coll] (with-meta (.-EMPTY List) meta))

  ISequential
  ISeq
  (-first [coll]
    (if (nil? s)
      [(aget nodes i) (aget nodes (inc i))]
      (first s)))

  (-rest [coll]
    (if (nil? s)
      (create-inode-seq nodes (+ i 2) dlen nloc nil)
      (create-inode-seq nodes i dlen nloc (next s))))

  ISeqable
  (-seq [this] this)

  IEquiv
  (-equiv [coll other] (equiv-sequential coll other))

  IHash
  (-hash [coll] (caching-hash coll hash-ordered-coll __hash))

  IReduce
  (-reduce [coll f] (seq-reduce f coll))
  (-reduce [coll f start] (seq-reduce f start coll)))

(defn- create-inode-seq [nodes i dlen nloc s]
  (if (nil? s)
    (cond
      (< i dlen)
      (NodeSeq. nil nodes dlen nloc i nil nil)
      (> nloc dlen)
      (NodeSeq. nil nodes dlen (dec nloc) i (.inode-seq (aget nodes nloc)) nil))
    (NodeSeq. nil nodes dlen nloc i s nil)))

(extend-protocol IPrintWithWriter
  NodeSeq
  (-pr-writer [coll writer opts] (pr-sequential-writer writer pr-writer "(" " " ")" opts coll))
  PersistentHashMap
  (-pr-writer [coll writer opts]
    (print-map coll pr-writer writer opts)))

(def lem (.-EMPTY PersistentHashMap))
(def cem (.-EMPTY cljs.core/PersistentHashMap))

(comment
  (let [times 50
        hm1 (loop [m lem i 0]
              (if (< i times)
                (recur (assoc m (str "key" i) i) (inc i))
                m))
        root (.-root hm1)]
    (println hm1)
    [ (count hm1)  (seq hm1)]
    #_[(count hm1)
       (= (into #{} (vals hm1)) (into #{} (range times)))
       (= (into #{} (vals hm1)) (into #{} (map #(get hm1 (str "key" %)) (range times))))
       (= (keys hm1) (map (fn [[k v]] k) hm1))
       (= (into #{} (keys hm1)) (into #{} (map #(str "key" %) (range times))))])
  )
