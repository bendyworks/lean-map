package com.bendyworks;

import java.util.Iterator;

import clojure.lang.APersistentMap;
import clojure.lang.IFn;
import clojure.lang.IMapEntry;
import clojure.lang.IPersistentCollection;
import clojure.lang.IPersistentMap;
import clojure.lang.ISeq;
import clojure.lang.MapEntry;
import clojure.lang.Util;
import clojure.lang.Box;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicReference;

public class LeanMap extends APersistentMap {

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

    public ISeq seq() { return null; } //TODO: Implement

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

    interface INode extends Serializable {
        INode assoc(AtomicReference<Thread> edit, int shift, int hash, Object key, Object val, Box added_leaf);

        Object find(int shift, int hash, Object key, Object not_found);

        IMapEntry find(int shift, int hash, Object key);
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
                return null; //TODO: Set to HashCollision Node
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
    }
}