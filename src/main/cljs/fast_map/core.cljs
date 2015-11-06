(ns cljs.fast-map.core
  (:refer-clojure :exclude [Box ->Box BitmapIndexedNode ->BitmapIndexedNode
                            HashCollisionNode ->HashCollisionNode
                            PersistentHashMap ->PersistentHashMap
                            TransientHashMap ->TransientHashMap
                            NodeSeq ->NodeSeq
                            create-inode-seq create-array-node-seq create-node
                            mask bitmap-indexed-node-index bitpos]))

(declare NodeSeq TransientHashMap PersistentHashMap)

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
  (and (not= x nil) (not= y nil) (identical? x y)))

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
          idx-new (- (alength arr) 2 (* 2 (bitmap-indexed-node-index nodemap bit)))
          dst (make-array (dec (alength arr)))]
      (array-copy arr 0 dst 0 idx-old)
      (array-copy arr (+ 2 idx-old) dst idx-old (- idx-new idx-old))
      (aset dst idx-new node)
      (array-copy arr (+ idx-new 2) dst (inc idx-new) (- (alength arr) idx-new 2))
      (BitmapIndexedNode. e (bit-or datamap bit) (bit-xor nodemap bit) dst)))

  (inode-seq [inode]
    (NodeSeq. nil arr (* 2 (bit-count datamap)) (bit-count nodemap) 0 nil nil))

  (inode-assoc [inode aedit shift hash key val changed?]
    (let [bit (bitpos hash shift)]
      (cond
        (not (zero? (bit-and datamap bit)))
        (let [idx (bitmap-indexed-node-index datamap bit)
              k (aget arr (* 2 idx))]
          (set! (.-modified changed?) true)
          (if (= k key)
            (do
              (set! (.-added changed?) true)
              (.copy-and-set-value inode aedit bit val))
            (let [v (aget arr (inc (* 2 idx)))
                  new-node (create-node aedit (+ shift 5) k v hash key val)]
              (.copy-and-migrate-to-node inode aedit bit new-node))))
        (not (zero? (bit-and nodemap bit)))
        (let [sub-node (node-at arr nodemap bit)
              sub-node-new (.inode_assoc sub-node shift hash key val changed?)]
          (if (.-modified changed?)
            (.copy-and-set-node inode aedit bit sub-node-new)
            inode))
        :else
        (let [n (bit-count datamap)
              idx (bitmap-indexed-node-index datamap bit)
              new-arr (make-array (* 2 (inc n)))]
          (array-copy arr 0 new-arr 0 (* 2 idx))
          (aset new-arr (* 2 idx) key)
          (aset new-arr (inc (* 2 idx)) val)
          (array-copy arr (* 2 idx) new-arr (* 2 (inc idx)) (* 2 (- n idx)))
          (set! (.-added changed?) true)
          (BitmapIndexedNode. aedit (bit-or datamap bit) nodemap new-arr))))))

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
          (HashCollisionNode. nil collision-hash cnt (clone-and-set arr (inc idx) val)))))))

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

  IEmptyableCollection
  (-empty [coll] (-with-meta (.-EMPTY PersistentHashMap) meta))

  IEquiv
  (-equiv [coll other] (equiv-map coll other))

  IHash
  (-hash [coll] (caching-hash coll hash-unordered-coll __hash))

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
    #_(cond (nil? k)    has-nil?
          (nil? root) false
          :else       (not (identical? (.inode-lookup root 0 (hash k) k lookup-sentinel)
                                       lookup-sentinel))))



  IEditableCollection
  (-as-transient [coll]
    (TransientHashMap. (js-obj) root cnt)))

(set! (.-EMPTY PersistentHashMap) (PersistentHashMap. nil 0 nil empty-unordered-hash))

(deftype TransientHashMap [^:mutable ^boolean edit
                           ^:mutable root
                           ^:mutable count]
  Object
  #_(conj! [tcoll o]
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
                            (.inode-assoc nil edit 0 (hash k) k v changed?))]
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
  (-conj! [tcoll val] #_(.conj! tcoll val))

  (-persistent! [tcoll] (.persistent! tcoll))

  ITransientAssociative
  (-assoc! [tcoll key val] (.assoc! tcoll key val)))

(deftype NodeSeq [meta nodes dlen nlen i s ^:mutable __hash]
  Object
  (toString [coll]
    (pr-str* coll))
  (equiv [this other]
    (-equiv this other))

  IMeta
  (-meta [coll] meta)

  IWithMeta
  (-with-meta [coll meta] (NodeSeq. meta nodes dlen nlen i s __hash))

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
      (let [len (alength nodes)]
        (cond
          (< i dlen)
          (NodeSeq. meta nodes dlen nlen (+ i 2) nil nil)
          (and (== i dlen) (not (zero? nlen)))
          (NodeSeq. meta nodes dlen nlen len (aget nodes len) nil)
          :else
          (NodeSeq. meta nodes dlen nlen (dec i) (aget nodes (dec i)) nil)))
      (NodeSeq. meta nodes dlen nlen i (next s) nil)))

  ISeqable
  (-seq [this] this)

  IEquiv
  (-equiv [coll other] (equiv-sequential coll other))

  IHash
  (-hash [coll] (caching-hash coll hash-ordered-coll __hash))

  IReduce
  (-reduce [coll f] (seq-reduce f coll))
  (-reduce [coll f start] (seq-reduce f start coll)))

(extend-protocol IPrintWithWriter
  PersistentHashMap
  (-pr-writer [coll writer opts]
    (print-map coll pr-writer writer opts)))

(comment
    (let [em (.-EMPTY PersistentHashMap)
          hm1 (loop [m em i 0]
                (if (< i 50)
                  (recur (assoc m (str "key" i) i) (inc i))
                  m))]
      (println hm1)))