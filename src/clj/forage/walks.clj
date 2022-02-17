;; Functions for generating random walks.
;; (Code s/b independent of MASON and plot libs (e.g. Hanami, Vega-Lite).)
(ns forage.walks
    (:require [utils.math :as m]
              [utils.random :as r]
              [clojure.math.numeric-tower :as nt]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; GENERATING RANDOM WALKS

(defn step-vector-fn
  "Returns a function of no arguments that returns a random mathematical 
  vector in the form of pair containing a direction dir, in radians, and a
  length len.  dir is uniformly distributed in [0,2pi] using PRNG instance 
  rng, and length is distributed according to distribution instance dist.
  If low and high arguments are given, the distribution is truncated so that
  lengths fall within [low, high].  (These vectors represent steps going from
  one \"stop\" to the next in a random walk.)  Example use:
    (def step-vecs (repeatedly 
                     (step-vector-fn (make-well19937)
                                     (make-powerlaw 1 2)
                                     1 100)))"
  ([rng dist] (fn [] [(r/next-radian rng) (r/next-double dist)]))
  ([rng dist low high]
   (fn [] [(r/next-radian rng) (r/next-double dist low high)])))

(defn subst-init-dir
  "Given a sequence step-seq of step vectors, i.e. [direction length] pairs,
  return a sequence that's the same except that the direction of the first 
  vector has been replaced by init-dir."
  [init-dir step-seq]
  (let [firststep  (vec (first step-seq))
        othersteps (rest step-seq)
        newstep    (assoc firststep 0 init-dir)]
    (cons newstep othersteps)))


;; I probably don't need both of the next two:

(defn vecs-upto-len
  "Given a desired total path length, and a sequence of step vectors, returns 
  a sequence of vectors, from the front of the sequence, whose lengths sum to 
  at least desired-total.  By default, the lengths are made to sum to exactly
  desired-total by reducing the length in the last step vector.  Add 
  ':trim false' or ':trim nil' to return a sequence with the last vector as it
  was in the input vecs sequence."
  [desired-total vecs & {trim :trim :or {trim true}}]
  (reduce 
    (fn [[tot-len out-vecs] [dir len :as v]]
        (if (< tot-len desired-total)          ; if not yet reached total
          [(+ tot-len len) (conj out-vecs v)]  ; keep conj'ing
          (reduced                             ; otherwise
            (if trim   ; by default shorten last len so tot-len = desired-total
              (let [overshoot (- tot-len desired-total) ; how much > desired?
                    [old-dir old-len] (last out-vecs)
                    newlast [old-dir (- old-len overshoot)]] ; subtract extra
                (conj (vec (butlast out-vecs)) newlast)) ; replace old last
              out-vecs)))) ; return constructed seq as is if trim was falsey
    [0 []]
    vecs))

;; Instead of the following, one could use 
;; (count (vecs-upto-len desired-total vecs))
;; This version is more efficient if you don't yet want to separate out 
;; the relevant vecs, but then you might have to trim the last element
;; later.  I might delete count-vecs-upto-len later.
(defn count-vecs-upto-len
  "Given a desired total path length, and a sequence of step vectors,
  returns the number of steps needed to sum lengths to at least 
  desired-total."
  [desired-total vecs]
  (reduce (fn [[tot-len cnt] [_ len]]
            (if (< tot-len desired-total)
              [(+ tot-len len) (inc cnt)]
              (reduced cnt)))
          [0 0]
          vecs))

(defn next-walk-stop
  "Given a mathematical vector in the form of a direction in radians
  and a length, and (a vector in the form of) a coordinate pair, returns 
  a new coordinate pair that's the result of adding the first vector
  to the second.  (This is the next \"stop\" in a walk.)"
  [[dir len] [prevx prevy]]
  (let [[vecx vecy] (m/rotate dir [len, 0]) ; rotate vector lying on x-axis
        nextx (+ prevx vecx)  ; add vector to prev point
        nexty (+ prevy vecy)]
    [nextx nexty]))

(defn walk-stops
  "Generates a (possibly infinite) sequence of next points from an 
  initial-point and a (possibly infinite) sequence of [direction, length]
  vectors, using each in turn, adding it to the previous point.  
  (These points are the \"stops\" in a random walk.)
  Example use, where step-vecs has been generated by repeated calls to 
  next-walk-fn:
     (walk-stops [0 0] step-vecs)"
  [prevpt step-vectors]
  (lazy-seq 
    (if-let [next-step-vec (first step-vectors)] ; nil if no more step-vecs
      (cons prevpt (walk-stops (next-walk-stop next-step-vec prevpt)
                               (rest step-vectors)))
      nil)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; FINDING FOOD

(defn slope-from-coords
  "Given a pair of points on a line, return its slope.  If the line is
  vertical, returns nil to indicate that."
  [[x1 y1] [x2 y2]]
  (if (= x1 x2)
    nil ; represents the vertical slope
    (/ (- y2 y1) (- x2 x1))))

;; See doc/xyshifts.md for detailed notes about this function and find-in-seg.
(defn xy-shifts
  "Given an incremental shift (vector) in the direction of a line specified 
  by its slope and intercept, return a pair [x-eps y-eps] that give
  the shifts in the x and y directions that would produce the desired shift
  (i.e. the vectors along x and y that would sum to the desired shift)."
  [eps slope]
  (println "eps is" eps "slope is" slope)
  (if slope ; if not vertical
    (let [a (+ 1 (* slope slope))
          x-eps (/ eps a)
          y-eps (nt/abs (* slope x-eps))]
      [x-eps y-eps]))
  [0 eps])

;; See doc/xyshifts.md for notes about this function and xy-shifts.
;; Possibly store slope and/or intercept earlier; they were available
;; when the line pair was created.
(defn find-in-seg
  "Given a pair of endpoints [x1 y1] and [x2 y2] on a line segment,
  and a small shift length, starts at [x1 y1] and incrementally checks
  points along the line segment at every shift length locations, checking 
  to see whether look-fn returns a truthy value representing one or more 
  foodspots from the perspective of that location, or a falsey value if
  no foodspots are found.  If foodspots are found, this function stops
  searching and returns a pair in which the first element is the coordinate
  pair for the location from which the foodspots were perceived, and the
  second element is the representation of the foodspots found, which may
  be a collection of foodspot objects, a collection of coordinates of
  foodspot objects, or some other truthy value.  (The kind of value to be
  returned depends on look-fun, which should reflect the way that this 
  function will be used.)  If no foodspots are found by the time [x2 y2]
  is checked, this function returns nil."
  [look-fn eps [x1 y1] [x2 y2]]
  ;(println "f-i-s:\nshift,[x1 y1],[x2 y2]:" eps [x1 y1] [x2 y2]) ; DEBUG
  (let [x-pos-dir? (<= x1 x2)
        y-pos-dir? (<= y1 y2)
        slope (slope-from-coords [x1 y1] [x2 y2])
        [x-eps y-eps] (xy-shifts eps slope)     ; x-eps, y-eps always >= 0
        x-shift (if x-pos-dir? x-eps (- x-eps)) ; correct their directions
        y-shift (if y-pos-dir? y-eps (- y-eps))
        x-comp (if x-pos-dir? > <)   ; and choose tests for when we've 
        y-comp (if y-pos-dir? > <)
        yow (atom true) ; DEBUG
        ]  ;  gone too far
    (prn x1 y1 x-shift y-shift) ; DEBUG
    (loop [x x1, y y1]
      (when @yow (swap! yow not) (prn x y)) ; DEBUG
      (if (or (Double/isNaN x) (Double/isNaN y))
        "\ndone: NaN"
        (let [food (look-fn [x y])]
          (cond food [[x y] food]
                (and (= x x2)
                     (= y y2))  nil ; last point. check both: horizontal or vertical lines
                :else  (let [xsh (+ x x-shift)
                             ysh (+ y y-shift)]
                         ;(println "x2,y2,xsh,ysh:" x2 y2 xsh ysh) ; DEBUG
                         (recur (if (x-comp xsh x2) x2 xsh) ; search from x2 if xsh went too far
                                (if (y-comp ysh y2) y2 ysh)))))))))

;; I might not care about the foodspot info returned,
;; but I might want to know when no food is found.  So the function has
;; to have a way to distinguish between running through the entire sequence
;; and finding nothing, and finding something from the very last point
;; in the sequence.  So the function also returns the foodspot info or nil
;; in order to--at least--communicate that difference.
(defn path-with-food
  "Given a sequence of stops (coordinate pairs) representing a random walk, 
  and a small eps length, starts at [x1 y1] and uses find-in-segment
  to incrementally check each line segment defined by pairs of stops
  to see whether look-fn returns a truthy value, meaning that foodspots
  were found.  The sequence stops must contain at least two coordinate pairs.
  If foodspots are found, returns a pair containing: first, a truncated 
  sequence of stops in which the last element is the point from which the
  food was seen, and remaining points have been removed, and second, the
  foodspot information returned by look-fn.  If no food found in the entire
  sequence, a pair contining the unchanged sequence and nil is returned."
  [look-fn eps stops]
  (let [stopsv (vec stops)
        numstops- (dec (count stops))] ; stop inc'ing two consecutive idxs one before length of stops vector
    (loop [i 0, j 1]
      (let [from+foodspots (find-in-seg look-fn eps (stopsv i) (stopsv j))]
        (if from+foodspots               ; all done--found food
          [(conj (vec (take j stopsv))    ; replace end of stops with point
                 (first from+foodspots))  ; on path from which food found
           (second from+foodspots)]
          (if (< j numstops-)
            (recur (inc i) (inc j))
            [stops nil])))))) ; no food in any segment; return entire input

(defn path-until-food
  [look-fn eps stops]
  (first (path-with-food look-fn eps stops)))

;; UNNEEDED?  path-until-found seems more useful.
;; otoh, path-until-found throws away the foodspots.
;; In the following function, the reason for returning the start of the
;; sequence along with the location from which food was found (followed
;; by info about what was found) is that these are the coordinate pairs 
;; that specify the final segment in the walk toward food found.  Having
;; that segment's coordinates allows one to for display it, calculate
;; its length, etc.  That's also why if food is not found, the function
;; returns the original coordinates of the last segment.
(defn find-food-in-walk
  "Given a sequence of stops (coordinate pairs) representing a random walk, 
  and a small shift length, starts at [x1 y1] and uses find-in-segment
  to incrementally check each line segment defined by pairs of stops
  to see whether look-fn returns a truthy value representing one
  or more foodspots from the perspective of that location, or a falsey value 
  if no foodspots are found.  The sequence stops must contain at least two
  coordinate pairs.  If foodspots are found, this function finishes searching 
  and returns a triple in which the first element is the coordinate pair for 
  the beginning of the segment on which the food was found, the second 
  element is the location from which the foodspots were perceived, and the
  third element is the representation of the foodspots found.  (See
  find-in-segment for more on the third element.)  If the input sequence
  ends without any food being found, the return value will be the coordinates
  of the last segment followed by nil."
  [look-fn eps stops]
  (loop [segments (partition 2 1 stops)]
    (let [[start end] (first segments)
          from-and-foodspots (find-in-seg look-fn eps start end)]
      (if from-and-foodspots
        (cons start from-and-foodspots) ; found some food
        (if-let [more (next segments)]
          (recur more)         ; keep searching
          [start end nil]))))) ; no food in all segments, so return last seg

