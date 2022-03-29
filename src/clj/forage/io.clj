(ns forage.io
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]))

;;;;;;;;;;;;;;;;;;

;; to write to file:
;; - seed at start of runs
;;     - for multiple runs with same params
;;     - for multiple parameter choices
;; - parameters for each set of runs
;; - count of foodspots found in a set of runs
;; 
;; - number of points or segments in each full walk (so we know how many
;;    PRNG calls there were in case want to fast-forward)
;; - or maybe just the total for a set of runs??
;; - maybe: overall length in (inches, whatever--not the number of segments
;;    or points) of walk up to food. Or this can be gotten later if needed by 
;;    re-running if necessary.

(defn spit-csv
  "Given a sequence of sequences of data in rows, opens a file and
  writes to it using write-csv.  options are those that can be passed
  to clojure.java.io/writer."
  [filename rows & options]
   (with-open [w (apply io/writer filename options)]
     (csv/write-csv w rows)))

(defn append-row-from-runs
  "Given a value for seed, a sequence of parameters, a count of found
  foodspots in a collection of runs, and the total number of segments in
  all of the full walks (before truncation due to finding food) in the
  collection, appends a new row to existing rows and returns the result.
  Apart from params being a sequence, there are no restrictions on content,
  so this can be used to write a row of labels as well."
  ([seed params found-count total-segment-count]
   (append-row-from-runs [] seed params found-count total-segment-count))
  ([prev-rows seed params found-count total-segment-count]
   (conj prev-rows (concat [seed] params [found-count total-segment-count]))))

(defn append-labels
  "Appends a new row with standard labels in addition to param-names.  Uses 
  append-row-from-runs."
  ([param-names]
   (append-row-from-runs "seed" param-names "found" "segments"))
  ([prev-rows param-names] 
   (append-row-from-runs prev-rows "seed" param-names "found" "segments")))
