(ns reschedul2.db.core
  (:require [monger.core :as mg]
            [monger.collection :as mc]
            [monger.query :as mq]
            [monger.operators :refer :all]
            [monger.result :refer [acknowledged?]]
            [mount.core :refer [defstate]]
            [buddy.hashers :as hashers]
            [reschedul2.config :refer [env]]
            [reschedul2.db.seed :refer [seed-admin-user]]
            [taoensso.timbre :as timbre]
            [ring.util.http-response :refer [not-found]])
  (:import (org.bson.types ObjectId)))
;
; Helpers
;

(defn stringify-id [res]
  (assoc res :_id (str (:_id res))))

(defn objectify-id [res]
  (assoc res :_id (ObjectId. (:_id res))))

(defn stringify-ids [res]
  (map (fn [item]
        (assoc item :_id (str (:_id item)))) (seq res)))

(defn objectify-ids [res]
  (map (fn [item]
        (assoc item :_id (ObjectId. (:_id item))) (seq res))))


(defstate db*
  :start (->
            env
            :database-url
            mg/connect-via-uri)
  :stop (-> db* :conn mg/disconnect))

(defstate db
  :start (:db db*))


; USERS
(defn create-user! [user]
  (mc/insert-and-return db "users" (merge user {:_id (ObjectId.)})))

; http://stackoverflow.com/questions/2342579/http-status-code-for-update-and-delete#2342589
; when updating password, does confirm- need to be dissoc'ed???

(defn update-user! [updated-user]
  (let
    [_id (ObjectId. (:_id updated-user))
     res (acknowledged? (mc/update-by-id db "users" _id (dissoc updated-user :_id)))]
    (stringify-id (mc/find-one-as-map db "users" {:_id _id}))))


(defn delete-user! [user]
  (let [_id (:_id user)]
    (acknowledged? (mc/remove-by-id db "users" _id))))
    ; if-let -> ID not found error


(defn get-user [id]
  (stringify-id
    (mc/find-one-as-map db "users" {:_id (ObjectId. id)})))

(defn get-all-users []
  (mq/with-collection
    db
    "users"
    (mq/find {})
    (mq/sort (array-map :name -1))))

(defn get-user-by-id [id]
  (mc/find-one-as-map db "users" {:_id (ObjectId. id)}))

(defn get-user-by-username [uname]
  (mc/find-one-as-map db "users" {:username uname}))

(defn get-user-by-email [email]
  (mq/with-collection
    db
    "users"
    (mq/find {:contact-info.email email})
    (mq/sort (array-map :last_name -1))))




(defn seed-database! []
  ; (let [data-dir (:seed-directory env)
  ;       directory (clojure.java.io/file data-dir)
  ;       files (file-seq directory)
  ;       seed (load-all-seed-venues files)]

  ; (timbre/info "seed venues to insert: " (count seed))
  (timbre/info "DB: " db)
  ;(println (str "\n\n\n" seed "\n\n\n"))
  ;(println (str "\n\n\n" (count (hash-map seed)) "\n\n\n"))

  ; WARNING : everything stubbed fresh on each reset!
  (mc/remove db "users")
  ; (mc/remove @db "venues")
  ; (mc/remove @db "proposals")

  ; (let [response (mc/insert-batch @db "venues" seed)]
  ;   (timbre/info (str "acknowledged?: " (acknowledged? response))))
  ; (timbre/info "seed venues to insert: " (count seed))))

  (timbre/info "created seed admin user")
  (create-user!
    (->
      seed-admin-user
      (dissoc :password-confirm)
      (update-in [:password] hashers/encrypt))))

      ;do other stuff...
