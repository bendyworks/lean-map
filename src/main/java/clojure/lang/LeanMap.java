package clojure.lang;

import java.util.Iterator;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicReference;

public class LeanMap extends APersistentMap implements IEditableCollection {

    final int count;
    final INode root;
    final IPersistentMap _meta;

    LeanMap(int count, INode root){
        this.count = count;
        this.root = root;
        this._meta = null;
    }

    public LeanMap(IPersistentMap meta, int count, INode root){
        this._meta = meta;
        this.count = count;
        this.root = root;
    }

    final public static LeanMap EMPTY = new LeanMap(0, null);
    final private static Object NOT_FOUND = new Object();

    public IPersistentMap assoc(Object key, Object val){
        Box added_leaf = new Box(null);
        INode new_root = (this.root == null ? BitmapIndexedNode.EMPTY : this.root)
                .assoc(null, 0, Util.hasheq(key), key, val, added_leaf);
        if(new_root == this.root) {
            return this;
        }
        return new LeanMap(meta(), added_leaf.val == null ? this.count : this.count + 1, new_root);
    }

    public boolean containsKey(Object key){
        return (root != null) && root.find(0, Util.hasheq(key), key, NOT_FOUND) != NOT_FOUND;
    }

    public IPersistentMap assocEx(Object key, Object val) {
        if(containsKey(key))
            throw Util.runtimeException("Key already present");
        return assoc(key, val);
    }

    public Object valAt(Object key, Object notFound){
        return root != null ? root.find(0, Util.hasheq(key), key, notFound) : notFound;
    }

    public Object valAt(Object key){
        return valAt(key, null);
    }

    public IMapEntry entryAt(Object key){
        return (root != null) ? root.find(0, Util.hasheq(key), key) : null;
    }

    public Iterator iterator() { return null; } //TODO: Implement

    public Iterator iterator(IFn f) {return null; } //TODO: Implement

    public IPersistentMap without(Object key) { return null; } //TODO: Implement

    public ISeq seq() { return  root != null ? root.nodeSeq() : null; }

    private static NodeSeq createINodeSeq(Object[] array, int lvl, INode[] nodes, int[] cursor_lengths, int data_idx, int data_len) {
        if (data_idx < data_len) {
            return new NodeSeq(null, array, lvl, nodes, cursor_lengths, (data_idx + 1), data_len);
        } else {
            while(lvl >= 0) {
                int node_idx = cursor_lengths[lvl];
                if (node_idx == 0) {
                    lvl = lvl - 1;
                } else {
                    cursor_lengths[lvl] = (node_idx - 1);

                    INode node = nodes[lvl].getNode(node_idx);
                    boolean has_nodes = node.hasNodes();
                    int new_lvl = has_nodes ? (lvl + 1) : lvl;

                    if (has_nodes) {
                        nodes[new_lvl] = node;
                        cursor_lengths[new_lvl] = node.nodeArity();
                    }

                    if (node.hasData()){
                        return new NodeSeq(null, node.getArray(), new_lvl, nodes, cursor_lengths, 0, (node.dataArity() - 1));
                    }

                    lvl = lvl + 1;
                }
            }
            return null;
        }
    }

    static final class NodeSeq extends ASeq {
        final Object[] array;
        final INode[] nodes;
        final int[] cursor_lengths;
        final int lvl;
        final int data_idx;
        final int data_len;

        NodeSeq(IPersistentMap meta, Object[] array, int lvl, INode[] nodes, int[] cursor_lengths, int data_idx, int data_len) {
            super(meta);
            this.array = array;
            this.nodes = nodes;
            this.lvl = lvl;
            this.cursor_lengths = cursor_lengths;
            this.data_idx = data_idx;
            this.data_len = data_len;
        }

        public Obj withMeta(IPersistentMap meta) {
            return new NodeSeq(meta, this.array, this.lvl, this.nodes, this.cursor_lengths, this.data_idx, this.data_len);
        }

        public Object first() {
            return MapEntry.create(array[(this.data_idx * 2)], array[((this.data_idx * 2) + 1)]);
        }

        public ISeq next() {
            return createINodeSeq(this.array, this.lvl, this.nodes, this.cursor_lengths, this.data_idx, this.data_len);
        }

    }

    public IPersistentMap meta(){
        return _meta;
    }

    public int count(){
        return count;
    }

    public LeanMap withMeta(IPersistentMap meta){
        return new LeanMap(meta, count, root);
    }

    public IPersistentCollection empty(){
        return EMPTY.withMeta(meta());
    }

    static int mask(int hash, int shift) {return (hash >>> shift) & 0x01f; }

    private static int bitpos(int hash, int shift){
        return 1 << mask(hash, shift);
    }

    private static boolean isAllowedToEdit(AtomicReference<Thread> x, AtomicReference<Thread> y) {
        return x != null && y != null && (x == y || x.get() == y.get());
    }

    public TransientLeanMap asTransient() {
        return new TransientLeanMap(this);
    }

    static final class TransientLeanMap extends ATransientMap {
        final AtomicReference<Thread> edit;
        volatile INode root;
        volatile int count;
        final Box leafFlag = new Box(null);

        TransientLeanMap(LeanMap m) {
            this(new AtomicReference<Thread>(Thread.currentThread()), m.root, m.count);
        }

        TransientLeanMap(AtomicReference<Thread> edit, INode root, int count) {
            this.edit = edit;
            this.root = root;
            this.count = count;
        }

        ITransientMap doAssoc(Object key, Object val) {
            leafFlag.val = null;
            INode n = ((this.root == null) ? BitmapIndexedNode.EMPTY : this.root)
                    .assoc(this.edit, 0, Util.hasheq(key), key, val, leafFlag);
            if (n != this.root) {
                this.root = n;
            }
            if(leafFlag.val != null) {
                this.count++;
            }
            return this;
        }

        IPersistentMap doPersistent() {
            this.edit.set(null);
            return new LeanMap(this.count, this.root);
        }

        Object doValAt(Object key, Object notFound) {
            return root.find(0, Util.hasheq(key), key, notFound);
        }

        int doCount() {
            return this.count;
        }

        void ensureEditable(){
            if(this.edit.get() == null) {
                throw new IllegalAccessError("Transient used after persistent! call");
            }
        }

        ITransientMap doWithout(Object key) { return null; } //TODO: Implement this
    }

    interface INode extends Serializable {
        INode assoc(AtomicReference<Thread> edit, int shift, int hash, Object key, Object val, Box added_leaf);

        Object find(int shift, int hash, Object key, Object not_found);

        IMapEntry find(int shift, int hash, Object key);

        ISeq nodeSeq();

        boolean hasData();

        boolean hasNodes();

        INode getNode(int node_idx);

        Object[] getArray();

        int nodeArity();

        int dataArity();
    }

    final static class BitmapIndexedNode implements INode {
        public static final BitmapIndexedNode EMPTY = new BitmapIndexedNode(null, 0, 0, new Object[0]);

        int datamap;
        int nodemap;
        Object[] array;
        final AtomicReference<Thread> edit;

        int bitmapNodeIndex(final int bitmap, final int bitpos) {
            return Integer.bitCount(bitmap & (bitpos - 1));
        }

        int nodeAt(final int bit) {
            return this.array.length - 1 - bitmapNodeIndex(this.nodemap, bit);
        }

        BitmapIndexedNode(AtomicReference<Thread> edit,  int datamap, int nodemap, Object[] array) {
            this.edit = edit;
            this.datamap = datamap;
            this.nodemap = nodemap;
            this.array = array;
        }

        private INode copyAndSet(AtomicReference<Thread> edit, int idx, Object val) {
            if (isAllowedToEdit(edit, this.edit)) {
                array[idx] = val;
                return this;
            } else {
                final Object[] new_array = this.array.clone();
                new_array[idx] = val;

                return new BitmapIndexedNode(edit, datamap, nodemap, new_array);
            }
        }

        private INode mergeTwoKeyValuePairs(AtomicReference<Thread> edit, int shift, int current_hash, Object current_key, Object current_val, int hash, Object key, Object val) {
            if ((32 < shift) && (current_hash == hash)) {
                return new HashCollisionNode(edit, current_hash, 2, new Object[] { current_key, current_val, key, val });
            } else {
                final int current_mask = mask(current_hash, shift);
                final int mask = mask(hash, shift);

                if (current_mask == mask) {
                    final INode new_node = mergeTwoKeyValuePairs(edit, (shift + 5), current_hash, current_key, current_val, hash, key, val);
                    return new BitmapIndexedNode(edit, 0, bitpos(current_hash, shift), new Object[] { new_node } );
                } else {
                    final int new_datamap = bitpos(current_hash, shift) | bitpos(hash, shift);

                    if (current_mask < mask) {
                        return new BitmapIndexedNode(edit, new_datamap, 0, new Object[] { current_key, current_val, key, val } );
                    } else {
                        return new BitmapIndexedNode(edit, new_datamap, 0, new Object[] { key, val, current_key, current_val } );
                    }
                }
            }
        }

        private INode copyAndMigrateToNode(AtomicReference<Thread> edit, int bit, INode node) {
            final int idx_old = (2 * bitmapNodeIndex(this.datamap, bit));
            final int idx_new = (this.array.length - 2 - bitmapNodeIndex(this.nodemap, bit));
            final Object[] dst = new Object[(this.array.length - 1)];

            System.arraycopy(this.array, 0, dst, 0, idx_old);
            System.arraycopy(this.array, (2 + idx_old), dst, idx_old, (idx_new - idx_old));
            dst[idx_new] = node;
            System.arraycopy(this.array, (2 + idx_new), dst, (idx_new + 1), (this.array.length - 2 - idx_new));

            return new BitmapIndexedNode(edit, (this.datamap ^ bit), (this.nodemap | bit), dst);
        }

        public INode assoc(AtomicReference<Thread> edit, int shift, int hash, Object key, Object val, Box added_leaf) {
            final int bit = bitpos(hash, shift);

            if ((this.datamap & bit) != 0) {
                final int idx = bitmapNodeIndex(this.datamap, bit);
                final Object current_key = this.array[(2 * idx)];
                if (Util.equals(key, current_key)) {
                    return copyAndSet(edit, ((2 * idx) + 1), val);
                } else {
                    final Object current_val = this.array[((2 * idx) + 1)];
                    final INode new_node = mergeTwoKeyValuePairs(edit, (shift + 5), Util.hasheq(current_key), current_key, current_val, hash, key, val);
                    added_leaf.val = added_leaf;

                    return copyAndMigrateToNode(edit, bit, new_node);
                }
            } else if ((this.nodemap & bit) != 0) {
                final int node_idx = nodeAt(bit);
                final INode sub_node = (INode) this.array[node_idx];
                final INode sub_node_new = sub_node.assoc(edit, (shift + 5), hash, key, val, added_leaf);

                if (sub_node == sub_node_new) {
                    return this;
                } else {
                    return copyAndSet(edit, node_idx, sub_node_new);
                }
            } else {
                final int n = this.array.length;
                final int idx = (2 * bitmapNodeIndex(datamap, bit));
                final Object[] new_array = new Object[(2 + n)];

                System.arraycopy(this.array, 0, new_array, 0, idx);
                new_array[idx] = key;
                new_array[(idx + 1)] = val;
                System.arraycopy(this.array, idx, new_array, (2 + idx), (n - idx));
                added_leaf.val = added_leaf;

                return new BitmapIndexedNode(edit, (this.datamap | bit), this.nodemap, new_array);
            }
        }

        public Object find(int shift, int hash, Object key, Object not_found) {
            final int bit = bitpos(hash, shift);

            if ((this.datamap & bit) != 0) {
                final int idx = bitmapNodeIndex(this.datamap, bit);
                final Object current_key = this.array[(2 * idx)];

                if (Util.equals(current_key, key)) {
                    return this.array[((2 * idx) + 1)];
                } else {
                    return not_found;
                }
            } else if ((this.nodemap & bit) != 0) {
                return ((INode) this.array[nodeAt(bit)]).find((shift + 5), hash, key, not_found);
            } else {
                return not_found;
            }
        }

        public IMapEntry find(int shift, int hash, Object key) {
            final int bit = bitpos(hash, shift);

            if ((this.datamap & bit) != 0) {
                final int idx = bitmapNodeIndex(this.datamap, bit);
                final Object current_key = this.array[(2 * idx)];

                if (Util.equals(current_key, key)) {
                    return new MapEntry(current_key, this.array[((2 * bit) + 1)]);
                } else {
                    return null;
                }
            } else if ((this.nodemap & bit) != 0) {
                return ((INode) this.array[nodeAt(bit)]).find((shift + 5), hash, key);
            } else {
                return null;
            }
        }

        public boolean hasNodes() {
            return this.nodemap != 0;
        }

        public boolean hasData() {
            return this.datamap != 0;
        }

        public int nodeArity() {
            return Integer.bitCount(this.nodemap);
        }

        public int dataArity() {
            return Integer.bitCount(this.datamap);
        }

        public INode getNode(int node_idx) {
            return (INode) this.array[(this.array.length - node_idx)];
        }

        public Object[] getArray() {
            return this.array;
        }

        public NodeSeq nodeSeq() {
            INode[] nodes = new INode[7];
            int[] cursor_lengths = new int[] {0, 0, 0, 0, 0, 0, 0};
            nodes[0] = this;
            cursor_lengths[0] = this.nodeArity();
            if (this.datamap == 0) {
                return createINodeSeq(this.array, 0, nodes, cursor_lengths, 0, 0);
            } else {
                return new NodeSeq(null, this.array, 0, nodes, cursor_lengths, 0, (this.dataArity() - 1));
            }
        }
    }

    final static class HashCollisionNode implements INode {
        final int hash;
        int count;
        Object[] array;
        final AtomicReference<Thread> edit;

        HashCollisionNode(AtomicReference<Thread> edit, int hash, int count, Object... array){
            this.edit = edit;
            this.hash = hash;
            this.count = count;
            this.array = array;
        }

        public int findIndex(Object key){
            for(int i = 0; i < 2*count; i+=2)
            {
                if(Util.equiv(key, array[i])) {
                    return i;
                }
            }
            return -1;
        }

        private INode mutableAssoc(int idx, Object key, Object val, Box addedLeaf) {
            if(idx == -1) {
                Object[] new_array = new Object[(array.length + 2)];
                System.arraycopy(array, 0, new_array, 0, array.length);
                new_array[array.length] = key;
                new_array[(array.length + 1)] = val;
                addedLeaf.val = addedLeaf;

                this.array = new_array;
                this.count = this.count + 1;
            } else {
                if(this.array[(idx + 1)] != val) {
                    this.array[(idx + 1)] = val;
                }
            }

            return this;
        }

        private INode persistentAssoc(int idx, Object key, Object val, Box addedLeaf) {
            if(idx == -1) {
                Object[] new_array = new Object[(array.length + 2)];
                System.arraycopy(array, 0, new_array, 0, array.length);
                new_array[array.length] = key;
                new_array[(array.length + 1)] = val;
                addedLeaf.val = addedLeaf;

                return new HashCollisionNode(this.edit, this.hash, (this.count + 1), new_array);
            } else {
                if(this.array[(idx + 1)] == val) {
                    return this;
                } else {
                    Object[] new_array = this.array.clone();
                    new_array[(idx + 1)] = val;

                    return new HashCollisionNode(this.edit, this.hash, (this.count + 1), new_array);
                }
            }
        }

        public INode assoc(AtomicReference<Thread> edit, int shift, int hash, Object key, Object val, Box addedLeaf){
            int idx = findIndex(key);
            if (isAllowedToEdit(edit, this.edit)) {
                HashCollisionNode new_node = (edit == this.edit) ? this : new HashCollisionNode(edit, this.hash, this.count, this.array.clone());
                return new_node.mutableAssoc(idx, key, val, addedLeaf);
            } else {
                return this.persistentAssoc(idx, key, val, addedLeaf);
            }
        }

        public Object find(int shift, int hash, Object key, Object notFound){
            int idx = findIndex(key);
            if(idx < 0) {
                return notFound;
            }
            if(Util.equiv(key, array[idx])) {
                return array[idx + 1];
            }
            return notFound;
        }

        public IMapEntry find(int shift, int hash, Object key){
            int idx = findIndex(key);
            if(idx < 0) {
                return null;
            }
            if(Util.equiv(key, array[idx])) {
                return MapEntry.create(array[idx], array[idx + 1]);
            }
            return null;
        }

        public boolean hasNodes() {
            return false;
        }

        public boolean hasData() {
            return true;
        }

        public int nodeArity() {
            return 0;
        }

        public int dataArity() {
            return this.count;
        }

        public INode getNode(int node_idx) {
            return null;
        }

        public Object[] getArray() {
            return this.array;
        }

        public NodeSeq nodeSeq() {
            throw new UnsupportedOperationException();
        }
    }
}