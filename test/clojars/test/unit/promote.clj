(ns clojars.test.unit.promote
  (:require [clojure.test :refer :all]
            [clojars.promote :refer :all]
            [clojure.java.io :as io]
            [clojars.db :as db]
            [clojars.config :refer [config]]
            [clojars.test.test-helper :as help]))

(use-fixtures :each
  help/default-fixture
  help/with-clean-database)

(defn copy-resource [version & [extension]]
  (let [extension (or extension "pom")]
    (.mkdirs (.getParentFile (file-for "robert" "hooke" version "")))
    (io/copy (io/reader (io/resource (str "hooke-" version "." extension)))
             (file-for "robert" "hooke" version extension))))

(deftest test-snapshot-blockers
  (is (= ["Snapshot versions cannot be promoted"]
         (blockers help/*db*
                   {:group "robert" :name "hooke"
                    :version "1.2.0-SNAPSHOT"}))))

(deftest test-metadata-blockers
  (copy-resource "1.1.1")
  (is (clojure.set/subset? #{"Missing url" "Missing description"}
                           (set (blockers help/*db*
                                          {:group "robert" :name "hooke"
                                           :version "1.1.1"})))))

(deftest test-unsigned
  (copy-resource "1.1.2")
  (let [b (blockers help/*db*
                    {:group "robert" :name "hooke" :version "1.1.2"})]
    (is (some #(.endsWith % "hooke-1.1.2.pom is not signed.") b))
    (is (some #(.endsWith % "hooke-1.1.2.jar is not signed.") b))
    (is (some #(= % "Missing file hooke-1.1.2.jar") b))))

(deftest test-success
  (copy-resource "1.1.2")
  (io/copy "dummy hooke jar file"
           (file-for "robert" "hooke" "1.1.2" "jar"))
  (copy-resource "1.1.2" "jar.asc")
  (copy-resource "1.1.2" "pom.asc")
  (db/add-user help/*db* "test@ex.com" "testuser" "password"
               (slurp (io/resource "pubring.gpg")))
  (db/add-member help/*db* "robert" "testuser" nil)
  (is (empty? (blockers help/*db*
                        {:group "robert" :name "hooke" :version "1.1.2"}))))

(deftest test-already-promoted
  (copy-resource "1.1.2")
  (io/copy "dummy hooke jar file"
           (file-for "robert" "hooke" "1.1.2" "jar"))
  (copy-resource "1.1.2" "jar.asc")
  (copy-resource "1.1.2" "pom.asc")
  (db/add-user help/*db* "test@ex.com" "testuser" "password"
               (slurp (io/resource "pubring.gpg")))
  (db/add-member help/*db* "robert" "testuser" nil)
  ;; FIXME: the test files are for 1.1.2, but the pom has 1.2.0,
  ;; and promote/blockers reads the pom to get the version, and expects
  ;; that version to be in the db
  (db/add-jar help/*db* "testuser" {:group "robert" :name "hooke" :version "1.2.0"})
  (db/promote help/*db* "robert" "hooke" "1.2.0")
  (is (= ["Already promoted."]
         (blockers help/*db* {:group "robert" :name "hooke" :version "1.1.2"}))))

(deftest test-failed-signature
  (copy-resource "1.1.2")
  (io/copy "dummy hooke jar file corrupted"
           (file-for "robert" "hooke" "1.1.2" "jar"))
  (copy-resource "1.1.2" "jar.asc")
  (copy-resource "1.1.2" "pom.asc")
  (db/add-user help/*db* "test@ex.com" "testuser" "password"
               (slurp (io/resource "pubring.gpg")))
  (db/add-member help/*db* "robert" "testuser" nil)
  (is (= [(str "Could not verify signature of "
               (config :repo) "/robert/hooke/1.1.2/hooke-1.1.2.jar. "
               "Ensure your public key is in your profile.")]
         (blockers help/*db*
                   {:group "robert" :name "hooke" :version "1.1.2"}))))

(deftest test-unknown-key-type
  (copy-resource "1.1.2")
  (io/copy "dummy hooke jar file"
           (file-for "robert" "hooke" "1.1.2" "jar"))
  (copy-resource "1.1.2" "jar.asc")
  (copy-resource "1.1.2" "pom.asc")
  (db/add-user help/*db* "test@ex.com" "testuser" "password"
               (slurp (io/resource "curve25519.key")))
  (db/add-member help/*db* "robert" "testuser" nil)
  (is (= (str "Could not verify signature of "
              (config :repo) "/robert/hooke/1.1.2/hooke-1.1.2.jar: "
              "unknown PGP public key algorithm encountered")
         (first
           (blockers help/*db*
                     {:group "robert" :name "hooke" :version "1.1.2"})))))

(deftest test-no-key
  (copy-resource "1.1.2")
  (io/copy "dummy hooke jar file corrupted"
           (file-for "robert" "hooke" "1.1.2" "jar"))
  (copy-resource "1.1.2" "jar.asc")
  (copy-resource "1.1.2" "pom.asc")
  (db/add-user help/*db* "test@ex.com" "testuser" "password" "")
  (db/add-member help/*db* "robert" "testuser" nil)
  (is (= [(str "Could not verify signature of "
               (config :repo) "/robert/hooke/1.1.2/hooke-1.1.2.jar. "
               "Ensure your public key is in your profile.")
          (str "Could not verify signature of "
               (config :repo) "/robert/hooke/1.1.2/hooke-1.1.2.pom. "
               "Ensure your public key is in your profile.")]
         (blockers help/*db*
                   {:group "robert" :name "hooke" :version "1.1.2"}))))

(letfn [(setup-source-repo []
          (copy-resource "1.1.2")
          (io/copy "dummy hooke jar file"
                   (file-for "robert" "hooke" "1.1.2" "jar"))
          (copy-resource "1.1.2" "jar.asc")
          (copy-resource "1.1.2" "pom.asc")
          (doseq [file-suffix ["jar" "pom"]
                  sum-suffix ["" ".asc"]
                  sig-suffix [".md5" ".sha1"]]
            (io/copy "dummy sig" (file-for "robert" "hooke" "1.1.2"
                                           (str file-suffix sum-suffix sig-suffix))))
          (db/add-user help/*db* "test@ex.com" "testuser" "password"
                       (slurp (io/resource "pubring.gpg")))
          (db/add-member help/*db* "robert" "testuser" nil)
          (db/add-jar help/*db* "testuser" {:group "robert" :name "hooke" :version "1.1.2"}))]

  (deftest promote-should-copy-to-releases-repo
    (setup-source-repo)
    (promote help/*db* {:group "robert" :name "hooke" :version "1.1.2"})
    (doseq [f (for [file-suffix [".jar" ".pom"]
                    sum-suffix ["" ".asc"]
                    sig-suffix ["" ".md5" ".sha1"]]
                (io/file (config :releases-repo) "robert" "hooke" "1.1.2"
                         (str "hooke-1.1.2" file-suffix sum-suffix sig-suffix)))]
      (is (.exists f))))

  (deftest promote-should-write-to-db-on-success
    (setup-source-repo)
    (is (not (db/promoted? help/*db* "robert" "hooke" "1.1.2")))
    (promote help/*db* {:group "robert" :name "hooke" :version "1.1.2"})
    (is (db/promoted? help/*db* "robert" "hooke" "1.1.2"))))