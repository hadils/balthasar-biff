(ns balthasar.video
  (:require
   [com.biffweb :as biff]
   [ring.util.response :as response]
   [clojure.string :as str]
   [clojure.java.io :as io]
   [cheshire.core :as json]
   [clojure.java.shell :refer [sh]]
   [clojure.core :as c])
  (:import
   (java.io File)))

(def allowed-types #{"video/mp4" "video/quicktime" "video/x-m4v"})
(def max-size (* 100 1024 1024)) ; 100MB

(defn convert-to-hls [input-path output-dir]
  (let [_ (-> (io/file output-dir) (.mkdirs))
        segment-path (str output-dir "/segment_%d.ts")
        playlist-path (str output-dir "/playlist.m3u8")
        result
        (sh "ffmpeg" "-i" input-path
            "-c:v" "libx264"
            "-preset" "fast"
            "-g" "48"
            "-sc_threshold" "0"
            "-hls_time" "6"        ; Duration of each segment in seconds
            "-hls_list_size" "0"    ; Keep all segments
            "-f" "hls"
            "-hls_segment_filename" segment-path
            playlist-path)
        #_(sh "ffmpeg" "-i" input-path
              "-map" "0:v" "-c:v" "libx264" "-crf" "23" "-preset" "medium" "-g" "48"
              "-map" "0:v" "-c:v" "libx264" "-crf" "28" "-preset" "fast" "-g" "48"
              "-map" "0:v" "-c:v" "libx264" "-crf" "32" "-preset" "fast" "-g" "48"
              "-map" "0:a" "-c:a" "aac" "-b:a" "128k"
              "-hls_time" "6" "-hls_playlist_type" "vod" "-hls_flags" "independent_segments"
              "-hls_segment_filename" segment-path
              "-report" "-f" "hls" playlist-path)]
    (println "FFmpeg result:" result)
    result))

(defn generate-video-id []
  (str (random-uuid)))

(defn error-response [string]
  {:status 400
   :headers {"Content-Type" "application/json"}
   :body (json/generate-string {:success false
                                :message string})})

(defn upload [file id content-type] (convert-to-hls (.getPath file) (str "target/resources/public/videos/" id "/")))

(defn handle-upload [{{{:keys [tempfile filename content-type size] :as params} "video"} :multipart-params}]
  (biff/pprint params)
  (try
    (cond
      (not (allowed-types content-type))
      (error-response (str "Invalid content type: " content-type))

      (> size max-size)
      (error-response "File too large")

      :else
      (let [video-id (generate-video-id)
            ext (last (str/split filename #"\."))
            new-filename (str video-id "." ext)
            cdn-url (str "http://localhost:8080/videos/" video-id "/playlist.m3u8")]

        (upload tempfile video-id content-type)

        (let [response {:status 200
                        :headers {"Content-Type" "application/json"}
                        :body (json/generate-string {;success true
                                                     :message "Upload successful"
                                                     :chapter-id video-id
                                                     :size size})}]
          (biff/pprint response)
          response)))

    (catch Exception e
      (biff/pprint e)
      {:status 500
       :headers {"Content-Type" "application/json"}
       :body (json/generate-string {:success false
                                    :message (.getMessage e)})})))

(defn serve-hls [req]
  (let [{{:keys [id file]} :path-params} req
        _ (biff/pprint (:headers req))
        playlist-path (str "target/resources/public/videos/" id "/" file)
        outfile (io/file playlist-path)]
    (if (.exists outfile)
      (if (= "playlist.m3u8" file)
        {:status 200
         :headers {"Content-Type" "application/x-mpegURL"
                   "Content-Length" (str (.length outfile))
                   "Access-Control-Allow-Origin" "*"}  ; If needed for CORS
         :body (slurp outfile)}
        (let [file-stream (io/input-stream outfile)]
          {:status 200
           :headers {"Content-Type" "video/mp2t"
                     "Content-Length" (str (.length outfile))
                     "Access-Control-Allow-Origin" "*"}  ; If needed for CORS
           :body file-stream}))
      {:status 400
       :body {:success false
              :message "Video not found"}})))
