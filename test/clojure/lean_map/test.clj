(ns clojure.lean-map.test
  (:require [clojure.lean-map.util :as lmu]
            [clojure.test :as t]))

(t/deftest assoc-operations
  (t/is (= :bar (-> lmu/empty (assoc :foo :bar) (get :foo))))
  (t/is (= :baz (-> lmu/empty (assoc :whee :bar) (get :foo :baz)))))

(t/deftest assoc-operations-with-hash-collisions
  (t/is (= :bar1 (-> lmu/empty (assoc :key70327 :bar1 :key101439 :bar2) (get :key70327))))
  (t/is (= :bar2 (-> lmu/empty (assoc :key70327 :bar1 :key101439 :bar2) (get :key101439)))))

(t/deftest transient-assoc-operations
  (t/is (= :bar (-> lmu/empty transient (assoc! :foo :bar) persistent! (get :foo))))
  (t/is (= :baz (-> lmu/empty transient (assoc! :whee :bar) persistent! (get :foo :baz)))))

(t/deftest transient-assoc-operations-with-hash-collisions
  (t/is (= :bar1 (-> lmu/empty transient (assoc! :key70327 :bar1 :key101439 :bar2) persistent! (get :key70327))))
  (t/is (= :bar2 (-> lmu/empty transient (assoc! :key70327 :bar1 :key101439 :bar2) persistent! (get :key101439)))))

(t/deftest seq-operations
  (t/is (= false (-> lmu/empty (assoc :foo :bar) seq empty?)))
  (t/is (= [:foo :bar] (-> lmu/empty (assoc :foo :bar) seq first))))