(ns clojars.tools.repair-metadata
  (:require [clojars.maven :as mvn]
            [clojars.file-utils :as futil]
            [clojure.java.io :as io])
  (:import (java.io File)
           (org.apache.commons.io FileUtils)
           (org.apache.maven.artifact.repository.metadata Metadata)
           (java.text SimpleDateFormat)
           (java.util Date))
  (:gen-class))

(defn find-bad-metadata [repo]
  ;; This is gross, mainly because maven uses maven-metadata.xml files for three things:
  ;; * data for SNAPSHOTS (within the x.x.x-SNAPSHOT dir)
  ;; * data for plugins (within the group dir)
  ;; * data for release versions (within the artifact dir)
  ;; and we only care about the last one, so have to filter out the other two
  (for [f (file-seq (io/file repo))
        :when (= "maven-metadata.xml" (.getName f))
        ;; ignore metadata within SNAPSHOT dir
        :when (not (.endsWith (.getName (.getParentFile f)) "SNAPSHOT"))
        :let [^Metadata metadata (try
                                   (mvn/read-metadata f)
                                   (catch Exception _
                                     (println "Failed to read" f)))]
        :when metadata
        ;; ignore plugin metadata files
        :when (not (seq (.getPlugins metadata)))
        :let [parent (.getParentFile ^File f)
              version-dirs (for [dir (file-seq parent)
                                 :when (.isDirectory dir)
                                 :when (not= dir parent)
                                 ;; filter out artifacts where the current dir is a group as well as an artifact
                                 :when (or (.endsWith (.getName dir) "SNAPSHOT")
                                           (not (.exists (io/file dir "maven-metadata.xml"))))]
                             dir)
              versions (set (map (memfn getName) version-dirs))
              missing-versions? (not= versions (set (.getVersions (.getVersioning metadata))))
              invalid-sums? (not (futil/valid-sums? f))]
        :when (or missing-versions? invalid-sums?)]
    {:file              f
     :metadata          metadata
     :group-id          (.getGroupId metadata)
     :artifact-id       (.getArtifactId metadata)
     :version-dirs      version-dirs
     :missing-versions? missing-versions?
     :invalid-sums?     invalid-sums?}))

(def date-formatter (SimpleDateFormat. "yyyyMMddHHmmss"))

(defn backup-metadata [backup-dir {:keys [file group-id artifact-id]}]
  (let [to-dir (doto (io/file backup-dir group-id artifact-id (.format date-formatter (Date.)))
                 .mkdirs)]
    (run! #(FileUtils/copyFileToDirectory % to-dir)
          (filter (memfn exists)
                  [file (futil/sum-file file :sha1) (futil/sum-file file :md5)]))))

(defn repair-versions [{:keys [file metadata version-dirs]}]
  (let [versioning (.getVersioning metadata)
        sorted-dirs (sort-by #(.lastModified %) version-dirs)]
    ;; remove existing versions, then write dir versions in dir creation order
    (run! #(.removeVersion versioning %) (into [] (.getVersions versioning)))
    (run! #(.addVersion versioning %) (map (memfn getName) sorted-dirs))
    ;; set release to latest !snapshot
    (.setRelease versioning
                 (->> sorted-dirs
                      (filter #(not (.endsWith (.getName %) "SNAPSHOT")))
                      last
                      .getName))
    ;; set lastUpdated to latest version
    (.setLastUpdated versioning (.format date-formatter
                                         (-> sorted-dirs last .lastModified Date.)))

    ;; write new file
    (mvn/write-metadata metadata file)))

(defn repair-metadata [backup-dir {:keys [file missing-versions?] :as data}]
  (backup-metadata backup-dir data)
  (when missing-versions?
    (repair-versions data))
  (futil/create-sums file))

(defn -main [& args]
  (if (not= 3 (count args))
    (println "Usage: repo-path backup-path (:repair|:report)")
    (let [[repo backup-dir action] args]
      (doseq [md (find-bad-metadata repo)]
        (if (= ":repair" action)
          (repair-metadata (io/file backup-dir) md)
          (prn md))))))
