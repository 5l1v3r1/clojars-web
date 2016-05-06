(ns clojars.tools.generate-feeds
  (:require [clojure.java.io :as io]
            [clojars.maven :as maven]
            [clojars.config :refer [config configure]]
            [clojure.set :as set]
            [clojars.db :as db]
            [clojars.cloudfiles :as cf]
            [clojars.file-utils :as fu])
  (:import java.util.zip.GZIPOutputStream
           (java.io FileOutputStream PrintWriter))
  (:gen-class))

(defn full-feed [db]
  (let [grouped-jars (->> (db/all-jars db)
                          (map (comp #(assoc % :url (:homepage %))
                                     #(select-keys % [:group-id :artifact-id :version
                                                      :description :scm :homepage])
                                     #(set/rename-keys % {:group_name :group-id
                                                          :jar_name   :artifact-id})))
                          (group-by (juxt :group-id :artifact-id))
                          (vals))]
    (for [jars grouped-jars]
      (let [jars (sort-by :version #(maven/compare-versions %2 %1) jars)]
        (-> (first jars)
            (dissoc :version)
            (assoc :versions (vec (distinct (map :version jars))))
            maven/without-nil-values)))))

(defn write-to-file
  ([data file gzip?]
    (write-to-file data file gzip? prn))
  ([data file gzip? out-fn]
   (with-open [w (-> (FileOutputStream. file)
                     (cond-> gzip? (GZIPOutputStream.))
                     (PrintWriter.))]
     (binding [*out* w]
       (doseq [form data]
         (out-fn form))))
   file))

(defn pom-list [cloudfiles]
  (sort
    (into []
          (comp (map :name)
                (filter #(.endsWith % ".pom"))
                ;; to match historical list format
                (map (partial str "./")))
          (cf/metadata-seq cloudfiles))))

(defn jar-list [db]
  (sort
    (map (fn [{:keys [group_name jar_name version]}]
           [(if (= group_name jar_name)
              (symbol jar_name)
              (symbol group_name jar_name))
            version])
         (db/all-jars db))))

(defn write-sums [f]
  [(fu/create-checksum-file f :md5)
   (fu/create-checksum-file f :sha1)])

(defn put-files [cloudfiles & files]
  (run! #(let [f (io/file %)]
          (cf/put-file cloudfiles (.getName f) f :if-changed))
        files))

(defn generate-feeds [dest db cloudfiles]
  (let [feed-file (str dest "/feed.clj.gz")]
    (apply put-files
           cloudfiles
           (write-to-file (full-feed db) feed-file :gzip)
           (write-sums feed-file)))

  (let [poms (pom-list cloudfiles)
        pom-file (str dest "/all-poms.txt")
        gz-file (str pom-file ".gz")]
    (apply put-files
           cloudfiles
           (write-to-file poms pom-file nil println)
           (write-to-file poms gz-file :gzip println)
           (concat
             (write-sums pom-file)
             (write-sums gz-file))))

  (let [jars (jar-list db)
        jar-file (str dest "/all-jars.clj")
        gz-file (str jar-file ".gz")]
    (apply put-files
           cloudfiles
           (write-to-file jars jar-file nil)
           (write-to-file jars gz-file :gzip)
           (concat
             (write-sums jar-file)
             (write-sums gz-file)))))

(defn -main [dest]
  (configure nil)
  (generate-feeds dest (:db config) (:cloudfiles config)))

