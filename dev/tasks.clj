(ns tasks
  (:require [com.biffweb.task-runner :refer [run-task]]
            [com.biffweb.tasks :as tasks]
            [com.biffweb.tasks.lazy.babashka.fs :as fs]
            [com.biffweb.tasks.lazy.babashka.process :refer [shell]]
            [com.biffweb.tasks.lazy.clojure.java.io :as io]
            [com.biffweb.tasks.lazy.com.biffweb.config :as config]
            [com.biffweb.tasks.lazy.clojure.tools.build.api :as clj-build]))

(def config (delay (config/use-aero-config {:biff.config/skip-validation true})))

(defn hello
  "Says 'Hello'"
  []
  (println "Hello"))

(defn dev
  "Starts the app locally.

   After running, wait for the `System started` message. Connect your editor to
   nrepl port 7888 (by default). Whenever you save a file, Biff will:

   - Evaluate any changed Clojure files
   - Regenerate static HTML and CSS files
   - Run tests

   Also starts the Shadow CLJS watcher."
  []
  (if-not (fs/exists? "target/resources")
    ;; This is an awful hack. We have to run the app in a new process, otherwise
    ;; target/resources won't be included in the classpath. Downside of not
    ;; using bb tasks anymore -- no longer have a lightweight parent process
    ;; that can create the directory before starting the JVM.
    (do
      (io/make-parents "target/resources/_")
      (shell "clj" "-M:dev" "dev"))
    (do
      (tasks/future {shell "npx tailwindcss -i resources/public/css/tailwind.css -o target/resource/public/css/main.css"})
      (tasks/future (shell "npx shadow-cljs watch app"))
      (tasks/dev))))

(defn deploy
  "Pushes code to the server and restarts the app.

   Uploads config and code to the server, using `rsync` if it's available, and
   `git push` otherwise. Then restarts the app.

   You must set up a server first. See https://biffweb.com/docs/reference/production/"
  []
  (shell "npx shadow-cljs release app")
  (tasks/deploy))

(defn soft-deploy
  "Pushes code to the server and evaluates changed files.

   1. Builds css and cljs
   2. Uploads files
   3. `eval`s any changed files
   4. Regenerates static html files

   Does not refresh or restart, so there isn't any downtime."
  []
  (shell "npx shadow-cljs release app")
  (tasks/soft-deploy))

(defn uberjar
  "Compiles the app into an Uberjar."
  []
  (println "Cleaning...")
  (run-task "clean")
  (println "Compiling CLJS...")
  (shell "npm install")
  (shell "npx shadow-cljs release app")
  (tasks/uberjar "--no-clean"))

;; Tasks should be vars (#'hello instead of hello) so that `clj -M:dev help` can
;; print their docstrings.
(def custom-tasks
  {"hello"       #'hello
   "dev"         #'dev
   "deploy"      #'deploy
   "soft-deploy" #'soft-deploy
   "uberjar"     #'uberjar})

(def tasks (merge tasks/tasks custom-tasks))