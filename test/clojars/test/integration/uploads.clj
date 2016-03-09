(ns clojars.test.integration.uploads
  (:require [cemerick.pomegranate.aether :as aether]
            [clojars
             [config :refer [config]]
             [web :as web :refer [clojars-app]]]
            [clojars.test.integration.steps :refer :all]
            [clojars.test.test-helper :as help]
            [clojure.data.codec.base64 :as base64]
            [clojure.java.io :as io]
            [clojure.test :refer :all]
            [kerodon
             [core :refer :all]
             [test :refer :all]]
            [net.cgrand.enlive-html :as enlive]))

(use-fixtures :each
  help/default-fixture
  help/run-test-app)

(deftest user-can-register-and-deploy
  (-> (session (help/app-from-system))
      (register-as "dantheman" "test@example.org" "password"))
  (help/delete-file-recursively help/local-repo)
  (help/delete-file-recursively help/local-repo2)
  (aether/deploy
   :coordinates '[org.clojars.dantheman/test "0.0.1"]
   :jar-file (io/file (io/resource "test.jar"))
   :pom-file (help/rewrite-pom (io/file (io/resource "test-0.0.1/test.pom"))
               {:groupId "org.clojars.dantheman"})
   :repository {"test" {:url (str "http://localhost:" help/test-port "/repo")
                        :username "dantheman"
                        :password "password"}}
   :local-repo help/local-repo)
  (is (= 6
         (count (.list (clojure.java.io/file (:repo config)
                                             "org"
                                             "clojars"
                                             "dantheman"
                                             "test"
                                             "0.0.1")))))
  (is (= '{[org.clojars.dantheman/test "0.0.1"] nil}
         (aether/resolve-dependencies
          :coordinates '[[org.clojars.dantheman/test "0.0.1"]]
          :repositories {"test" {:url
                                 (str "http://localhost:" help/test-port "/repo")}}
          :local-repo help/local-repo2)))
  (-> (session (help/app-from-system))
      (visit "/groups/org.clojars.dantheman")
      (has (status? 200))
      (within [:#content-wrapper
               [:ul enlive/last-of-type]
               [:li enlive/only-child]
               :a]
              (has (text? "dantheman")))
      (follow "org.clojars.dantheman/test")
      (has (status? 200))
      (within [:#jar-sidebar :li.homepage :a]
              (has (text? "https://example.org"))))
  (-> (session (help/app-from-system))
      (visit "/")
      (fill-in [:#search] "test")
      (press [:#search-button])
      (within [:div.result]
        (has (text? "org.clojars.dantheman/test 0.0.1")))))

(deftest user-can-deploy-to-new-group
   (-> (session (help/app-from-system))
       (register-as "dantheman" "test@example.org" "password"))
   (help/delete-file-recursively help/local-repo)
   (help/delete-file-recursively help/local-repo2)
   (aether/deploy
    :coordinates '[fake/test "0.0.1"]
    :jar-file (io/file (io/resource "test.jar"))
    :pom-file (io/file (io/resource "test-0.0.1/test.pom"))
    :repository {"test" {:url (str "http://localhost:" help/test-port "/repo")
                         :username "dantheman"
                         :password "password"}}
    :local-repo help/local-repo)
   (is (= '{[fake/test "0.0.1"] nil}
          (aether/resolve-dependencies
           :coordinates '[[fake/test "0.0.1"]]
           :repositories {"test" {:url
                                  (str "http://localhost:" help/test-port "/repo")}}
           :local-repo help/local-repo2)))
   (-> (session (help/app-from-system))
       (visit "/groups/fake")
       (has (status? 200))
       (within [:#content-wrapper
                [:ul enlive/last-of-type]
                [:li enlive/only-child]
                :a]
               (has (text? "dantheman")))
       (follow "fake/test")
       (has (status? 200))
       (within [:#jar-sidebar :li.homepage :a]
               (has (text? "https://example.org")))))

(deftest user-cannot-deploy-to-groups-without-permission
  (-> (session (help/app-from-system))
      (register-as "dantheman" "test@example.org" "password"))
  (-> (session (help/app-from-system))
      (register-as "fixture" "fixture@example.org" "password"))
  (is (thrown-with-msg? org.sonatype.aether.deployment.DeploymentException
        #"Forbidden"
        (aether/deploy
         :coordinates '[org.clojars.fixture/test "1.0.0"]
         :jar-file (io/file (io/resource "test.jar"))
         :pom-file (io/file (io/resource "test-0.0.1/test.pom"))
         :repository {"test" {:url (str "http://localhost:" help/test-port "/repo")
                              :username "dantheman"
                              :password "password"}}
         :local-repo help/local-repo))))

(deftest user-cannot-redeploy
  (-> (session (help/app-from-system))
      (register-as "dantheman" "test@example.org" "password"))
  (aether/deploy
   :coordinates '[fake/test "0.0.1"]
   :jar-file (io/file (io/resource "test.jar"))
   :pom-file (io/file (io/resource "test-0.0.1/test.pom"))
   :repository {"test" {:url (str "http://localhost:" help/test-port "/repo")
                        :username "dantheman"
                        :password "password"}}
   :local-repo help/local-repo)
  (is (thrown-with-msg?
        org.sonatype.aether.deployment.DeploymentException
        #"Forbidden - redeploying non-snapshots"
        (aether/deploy
          :coordinates '[fake/test "0.0.1"]
          :jar-file (io/file (io/resource "test.jar"))
          :pom-file (io/file (io/resource "test-0.0.1/test.pom"))
          :repository {"test" {:url (str "http://localhost:" help/test-port "/repo")
                               :username "dantheman"
                               :password "password"}}
          :local-repo help/local-repo))))

(deftest user-can-redeploy-snapshots
  (-> (session (help/app-from-system))
      (register-as "dantheman" "test@example.org" "password"))
  (aether/deploy
   :coordinates '[fake/test "0.0.3-SNAPSHOT"]
   :jar-file (io/file (io/resource "test.jar"))
   :pom-file (io/file (io/resource "test-0.0.3-SNAPSHOT/test.pom"))
   :repository {"test" {:url (str "http://localhost:" help/test-port "/repo")
                        :username "dantheman"
                        :password "password"}}
   :local-repo help/local-repo)
  (aether/deploy
   :coordinates '[fake/test "0.0.3-SNAPSHOT"]
   :jar-file (io/file (io/resource "test.jar"))
   :pom-file (io/file (io/resource "test-0.0.3-SNAPSHOT/test.pom"))
   :repository {"test" {:url (str "http://localhost:" help/test-port "/repo")
                        :username "dantheman"
                        :password "password"}}
   :local-repo help/local-repo))

(deftest user-can-deploy-snapshot-with-dot
  (-> (session (help/app-from-system))
      (register-as "dantheman" "test@example.org" "password"))
  (aether/deploy
   :coordinates '[org.clojars.dantheman/test.thing "0.0.3-SNAPSHOT"]
   :jar-file (io/file (io/resource "test.jar"))
   :pom-file (help/rewrite-pom (io/file (io/resource "test-0.0.3-SNAPSHOT/test.pom"))
               {:groupId "org.clojars.dantheman"
                :artifactId "test.thing"})
   :repository {"test" {:url (str "http://localhost:" help/test-port "/repo")
                        :username "dantheman"
                        :password "password"}}
   :local-repo help/local-repo))

(deftest user-can-deploy-with-classifiers
  (-> (session (help/app-from-system))
    (register-as "dantheman" "test@example.org" "password"))
  (aether/deploy
    :coordinates '[fake/test "0.0.1"]
    :artifact-map {[:extension "jar"] (io/file (io/resource "test.jar"))
                   [:classifier "test" :extension "jar"] (io/file (io/resource "test.jar"))
                   [:extension "pom"] (io/file (io/resource "test-0.0.1/test.pom"))}
    :repository {"test" {:url (str "http://localhost:" help/test-port "/repo")
                         :username "dantheman"
                         :password "password"}}
    :local-repo help/local-repo)
  (are [ext] (.exists (io/file (:repo help/test-config) "fake" "test" "0.0.1"
                        (format "test-0.0.1%s" ext)))
       ".pom" ".jar" "-test.jar"))

(deftest user-can-deploy-snapshot-with-classifiers
  (-> (session (help/app-from-system))
    (register-as "dantheman" "test@example.org" "password"))
  (aether/deploy
    :coordinates '[fake/test "0.0.3-SNAPSHOT"]
    :artifact-map {[:extension "jar"] (io/file (io/resource "test.jar"))
                   [:classifier "test" :extension "jar"] (io/file (io/resource "test.jar"))
                   [:extension "pom"] (io/file (io/resource "test-0.0.3-SNAPSHOT/test.pom"))}
    :repository {"test" {:url (str "http://localhost:" help/test-port "/repo")
                         :username "dantheman"
                         :password "password"}}
    :local-repo help/local-repo)
  ;; we can't look for files directly since the version will be timestamped
  (is (= 3 (->> (file-seq (io/file (:repo help/test-config) "fake" "test" "0.0.3-SNAPSHOT"))
             (filter (memfn isFile))
             (filter #(re-find #"(pom|jar|-test\.jar)$" (.getName %)))
             count))))

(deftest user-can-deploy-with-signatures
  (-> (session (help/app-from-system))
    (register-as "dantheman" "test@example.org" "password"))
  (let [pom (io/file (io/resource "test-0.0.1/test.pom"))]
    (aether/deploy
      :coordinates '[fake/test "0.0.1"]
      :artifact-map {[:extension "jar"] (io/file (io/resource "test.jar"))
                     [:extension "pom"] pom
                     ;; any content will do since we don't validate signatures
                     [:extension "jar.asc"] pom
                     [:extension "pom.asc"] pom}
      :repository {"test" {:url (str "http://localhost:" help/test-port "/repo")
                           :username "dantheman"
                           :password "password"}}
      :local-repo help/local-repo)))

(deftest missing-signature-fails-the-deploy
  (-> (session (help/app-from-system))
    (register-as "dantheman" "test@example.org" "password"))
  (let [pom (io/file (io/resource "test-0.0.1/test.pom"))]
    (is (thrown-with-msg? org.sonatype.aether.deployment.DeploymentException
          #"test-0.0.1.pom has no signature"
          (aether/deploy
            :coordinates '[fake/test "0.0.1"]
            :artifact-map {[:extension "jar"] (io/file (io/resource "test.jar"))
                           [:extension "pom"] pom
                           ;; any content will do since we don't validate signatures
                           [:extension "jar.asc"] pom}
            :repository {"test" {:url (str "http://localhost:" help/test-port "/repo")
                                 :username "dantheman"
                                 :password "password"}}
            :local-repo help/local-repo)))))

(deftest anonymous-cannot-deploy
  (is (thrown-with-msg? org.sonatype.aether.deployment.DeploymentException
        #"Unauthorized"
        (aether/deploy
         :coordinates '[fake/test "1.0.0"]
         :jar-file (io/file (io/resource "test.jar"))
         :pom-file (io/file (io/resource "test-0.0.1/test.pom"))
         :repository {"test" {:url (str "http://localhost:" help/test-port "/repo")}}
         :local-repo help/local-repo))))

(deftest bad-login-cannot-deploy
  (is (thrown? org.sonatype.aether.deployment.DeploymentException
        (aether/deploy
         :coordinates '[fake/test "1.0.0"]
         :jar-file (io/file (io/resource "test.jar"))
         :pom-file (io/file (io/resource "test-0.0.1/test.pom"))
         :repository {"test" {:url (str "http://localhost:" help/test-port "/repo")
                              :username "guest"
                              :password "password"}}
         :local-repo help/local-repo))))

(deftest deploy-requires-path-to-match-pom
  (-> (session (help/app-from-system))
    (register-as "dantheman" "test@example.org" "password"))
  (is (thrown-with-msg? org.sonatype.aether.deployment.DeploymentException
        #"Forbidden - the group in the pom \(fake\) does not match the group you are deploying to \(flake\)"
        (aether/deploy
         :coordinates '[flake/test "0.0.1"]
         :jar-file (io/file (io/resource "test.jar"))
         :pom-file (io/file (io/resource "test-0.0.1/test.pom"))
         :repository {"test" {:url (str "http://localhost:" help/test-port "/repo")
                              :username "dantheman"
                              :password "password"}}
         :local-repo help/local-repo)))
  (is (thrown-with-msg? org.sonatype.aether.deployment.DeploymentException
        #"Forbidden - the name in the pom \(test\) does not match the name you are deploying to \(toast\)"
        (aether/deploy
         :coordinates '[fake/toast "0.0.1"]
         :jar-file (io/file (io/resource "test.jar"))
         :pom-file (io/file (io/resource "test-0.0.1/test.pom"))
         :repository {"test" {:url (str "http://localhost:" help/test-port "/repo")
                              :username "dantheman"
                              :password "password"}}
         :local-repo help/local-repo)))
  (is (thrown-with-msg? org.sonatype.aether.deployment.DeploymentException
        #"Forbidden - the version in the pom \(0.0.1\) does not match the version you are deploying to \(1.0.0\)"
        (aether/deploy
         :coordinates '[fake/test "1.0.0"]
         :jar-file (io/file (io/resource "test.jar"))
         :pom-file (io/file (io/resource "test-0.0.1/test.pom"))
         :repository {"test" {:url (str "http://localhost:" help/test-port "/repo")
                              :username "dantheman"
                              :password "password"}}
         :local-repo help/local-repo))))

(deftest deploy-requires-lowercase-group
  (-> (session (help/app-from-system))
      (register-as "dantheman" "test@example.org" "password"))
  (is (thrown-with-msg? org.sonatype.aether.deployment.DeploymentException
        #"Forbidden - group names must consist solely of lowercase"
        (aether/deploy
         :coordinates '[faKE/test "0.0.1"]
         :jar-file (io/file (io/resource "test.jar"))
         :pom-file (help/rewrite-pom (io/file (io/resource "test-0.0.1/test.pom"))
                     {:groupId "faKE"})
         :repository {"test" {:url (str "http://localhost:" help/test-port "/repo")
                              :username "dantheman"
                              :password "password"}}
         :local-repo help/local-repo)))

  (deftest deploy-requires-lowercase-project
    (-> (session (help/app-from-system))
      (register-as "dantheman" "test@example.org" "password"))
    (is (thrown-with-msg? org.sonatype.aether.deployment.DeploymentException
          #"Forbidden - project names must consist solely of lowercase"
          (aether/deploy
            :coordinates '[fake/teST "0.0.1"]
            :jar-file (io/file (io/resource "test.jar"))
            :pom-file (help/rewrite-pom (io/file (io/resource "test-0.0.1/test.pom"))
                        {:artifactId "teST"})
            :repository {"test" {:url (str "http://localhost:" help/test-port "/repo")
                                 :username "dantheman"
                                 :password "password"}}
            :local-repo help/local-repo)))))

(deftest deploy-requires-ascii-version
  (-> (session (help/app-from-system))
      (register-as "dantheman" "test@example.org" "password"))
  (is (thrown-with-msg? org.sonatype.aether.deployment.DeploymentException
        #"Forbidden - version strings must consist solely of letters"
        (aether/deploy
         :coordinates '[fake/test "1.α.0"]
         :jar-file (io/file (io/resource "test.jar"))
         :pom-file (help/rewrite-pom (io/file (io/resource "test-0.0.1/test.pom"))
                     {:version "1.α.0"})
         :repository {"test" {:url (str "http://localhost:" help/test-port "/repo")
                              :username "dantheman"
                              :password "password"}}
         :local-repo help/local-repo))))

(deftest put-on-html-fails
  (-> (session (help/app-from-system))
      (register-as "dantheman" "test@example.org" "password")
      (visit "/repo/group/artifact/1.0.0/injection.html"
             :request-method :put
             :headers {"authorization"
                       (str "Basic "
                            (String. (base64/encode
                                      (.getBytes "dantheman:password"
                                                 "UTF-8"))
                                     "UTF-8"))}
             :body "XSS here")
      (has (status? 400))))

(deftest put-using-dotdot-fails
  (-> (session (help/app-from-system))
      (register-as "dantheman" "test@example.org" "password")
      (visit "/repo/../artifact/1.0.0/test.jar" :request-method :put
             :headers {"authorization"
                       (str "Basic "
                            (String. (base64/encode
                                      (.getBytes "dantheman:password"
                                                 "UTF-8"))
                                     "UTF-8"))}
             :body "Bad jar")
      (has (status? 400))
      (visit "/repo/group/..%2F..%2Fasdf/1.0.0/test.jar" :request-method :put
             :headers {"authorization"
                       (str "Basic "
                            (String. (base64/encode
                                      (.getBytes "dantheman:password"
                                                 "UTF-8"))
                                     "UTF-8"))}
             :body "Bad jar")
      (has (status? 400))
      (visit "/repo/group/artifact/..%2F..%2F..%2F1.0.0/test.jar"
             :request-method :put
             :headers {"authorization"
                       (str "Basic "
                            (String. (base64/encode
                                      (.getBytes "dantheman:password"
                                                 "UTF-8"))
                                     "UTF-8"))}
             :body "Bad jar")
      (has (status? 400))
      (visit "/repo/group/artifact/1.0.0/..%2F..%2F..%2F..%2F/test.jar"
             :request-method :put
             :headers {"authorization"
                       (str "Basic "
                            (String. (base64/encode
                                      (.getBytes "dantheman:password"
                                                 "UTF-8"))
                                     "UTF-8"))}
             :body "Bad jar")
      (has (status? 400))))

(deftest does-not-write-incomplete-file
  (-> (session (help/app-from-system))
      (register-as "dantheman" "test@example.org" "password"))
  (with-out-str
    (-> (session (help/app-from-system))
        (visit "/repo/group3/artifact3/1.0.0/test.jar"
               :body (proxy [java.io.InputStream] []
                       (read
                         ([] (throw (java.io.IOException.)))
                         ([^bytes bytes] (throw (java.io.IOException.)))
                         ([^bytes bytes off len] (throw (java.io.IOException.)))))
               :request-method :put
               :content-length 1000
               :content-type "txt/plain"
               :headers {:content-length 1000
                         :content-type "txt/plain"
                         :authorization (str "Basic "
                                             (String. (base64/encode
                                                       (.getBytes "dantheman:password"
                                                                  "UTF-8"))
                                                      "UTF-8"))})
        (has (status? 403))))
  (is (not (.exists (clojure.java.io/file (:repo config)
                                          "group3"
                                          "artifact3"
                                          "1.0.0"
                                          "test.jar")))))
