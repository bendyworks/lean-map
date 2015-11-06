(ns cljs.fast-map.core
  (:refer-clojure :exclude [Box ->Box BitmapIndexedNode ->BitmapIndexedNode
                            HashCollisionNode ->HashCollisionNode
                            create-inode-seq create-array-node-seq create-node
                            mask bitmap-indexed-node-index bitpos]))

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

(comment
    (let [em (.-EMPTY BitmapIndexedNode)
          sent (js-obj)]
      (let [bn1 (.inode-assoc em nil 0 (hash :foo1) :foo1 :bar  (Box. false false))
            bn2 (.inode-assoc bn1 nil 0 (hash :foo1) :foo1 :not-bar  (Box. false false))
            bn3 (.inode-assoc bn1 nil 0 (hash :foo2) :foo2 :bar2  (Box. false false))
            nn1 (.inode-assoc bn3 nil 0 (hash :foo2) :foo3 :split-bar  (Box. false false))]
        (println "Pesistent Assoc")
        (println [(.-arr em) (.-arr bn1) (.-arr bn2) (.-arr bn3) (.-arr nn1)
                  (.-arr (aget (.-arr nn1) 2))]))
      (let [bn1 (.inode-assoc em nil 0 (hash :foo1) :foo1 :bar  (Box. false false))
            tbn1 (.inode-assoc bn1 sent 0 (hash :foo1) :foo1 :my-bar  (Box. false false))
            tbn2 (.inode-assoc tbn1 sent 0 (hash :foo1) :foo1 :another-bar  (Box. false false))
            tbn3 (.inode-assoc tbn2 sent 0 (hash :foo1) :foo1 :changed  (Box. false false))]
        (println "Transient Assoc")
        (println [(.-arr em) (.-arr bn1) (.-arr tbn1) (.-arr tbn2) (.-arr tbn3)])) ))