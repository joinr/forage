(ns utils.math
    (:require [clojure.math.numeric-tower :as nt]
              [clojure.string :as st]))

; [fastmath.core :as fm]
; (use-primitive-operators)
; (unuse-primitive-operators)


;; TODO Consider revising to use clojure.math in Clojure 1.11:
;; https://clojure.org/news/2022/03/22/clojure-1-11-0
;; https://clojure.github.io/clojure/clojure.math-api.html


(defn remove-decimal-pt
  "Given a number, returns a (base-10) string representation of the
  number, but with any decimal point removed.  Also works on existing
  string representations of numbers."
  [x]
  (apply str 
         (st/split (str x) #"\.")))
  

;; Make my code a little prettier, and allow passing as functions:
(def pi Math/PI)
(defn cos [theta] (Math/cos theta))
(defn sin [theta] (Math/sin theta))
(defn tan [theta] (Math/tan theta))

(defn ln [x] (Math/log x))
(defn log [base x] (/ (ln x) (ln base)))

;; Based on https://en.wikipedia.org/wiki/Spiral#Two-dimensional
(defn archimedean-spiral-pt
  "Returns 2D coordinates of a point on an Archimedean spiral
  corresponding to input theta (which may be any positive real).
  Parameter a determines how widely separated the arms are."
  [a theta]
  (let [r (* a theta)]
    [(* r (cos theta)) (* r (sin theta))]))

(defn archimedean-spiral
  "Returns an infinite sequence of 2D coordinates of points on an
  Archimedean spiral around the origin.  Parameter a determines how
  widely separated the arms are.  increment is the distance between
  input values in radians; it determines the smoothness of a plot.
  If x and y are provided, they move the center of the spiral to [x y]."
  ([a increment] (map (fn [x] (archimedean-spiral-pt a (* increment x)))
                      (range)))
  ([a increment x y] (map (fn [[x' y']] [(+ x' x) (+ y' y)])
                          (archimedean-spiral a increment))))

(defn scaled-archimedean-spiral
  "Returns an infinite sequence of 2D coordinates of points on an
  Archimedean spiral around the origin.  Parameter scale determines how
  widely separated the arms are.  scale=1 causes the arms to be
  separated by 1 unit along any line from the center of the spiral.
  sincrement is the distance between input values in radians; it
  determines the smoothness of a plot.  If x and y are provided, they
  move the center of the spiral to [x y]."
  ([scale increment]
   (archimedean-spiral (* scale (/ 1 2 pi)) increment))
  ([scale increment x y] 
   (archimedean-spiral (* scale (/ 1 2 pi)) increment x y)))


(comment
  (require '[forage.viz.hanami :as h])
  (require '[oz.core :as oz])
  (oz/start-server!)

  (def xs (archimedean-spiral 0.05 0.1 5 5))
  (def vs (h/add-walk-labels "spiral" xs))
  (def plot (h/vega-walk-plot 600 10 1.0 (take 200 vs)))
  (oz/view! plot)
  
  (def pi2inv (/ 1 2 pi))
  (def xs (archimedean-spiral pi2inv 0.01 6 6))
  (def vs (h/add-walk-labels "spiral" xs))
  (def plot (h/vega-walk-plot 600 14 1.0 (take 4000 vs)))
  (oz/view! plot)

  (def xs (scaled-archimedean-spiral 2 0.1 10 10))
  (def vs (h/add-walk-labels "spiral" xs))
  (def plot (h/vega-walk-plot 600 21 1.0 (take 300 vs)))
  (oz/view! plot)

  ;(require '[nextjournal.clerk :as clerk])
  ;(clerk/serve! {:browse? true :watch-paths ["src/clj"]})
  ;(clerk/vl plot)
)


(defn bool-to-bin
  "Returns 1 if x is truthy, 0 if it's falsey."
  [x]
  (if x 1 0))

(defn sign
  [x]
  (cond (pos? x) 1
        (neg? x) -1
        :else 0))

;; Note that Java's Double/isInfinite and Float/isInfinite don't distinguish 
;; between ##Inf and ##-Inf.
(defn pos-inf?
  "Returns true if and only if x is ##Inf."
  [x]
  (= x ##Inf))

;; Added to Clojure in 1.11
;; Just a wrapper for Double/isNaN
;(defn NaN?
;  "Returns true if and only if x is ##NaN."
;  [x]
;  (Double/isNaN x))

(defn slope-from-coords
  "Given a pair of points on a line, return its slope.  This is also the
  vector direction from the first point to the second.  If the line is
  vertical, returns ##Inf (infinity) to indicate that."
  [[x1 y1] [x2 y2]]
  (if (== x1 x2)
    ##Inf ; infinity is what division below would give for the vertical slope
    (/ (- y2 y1) (- x2 x1))))

;; y = mx + b  so  b = y - mx
(defn intercept-from-slope
  "Given a slope and a point on a line, return the line's y intercept."
  [slope [x y]]
  (- y (* slope x)))

(defn equalish?
  "True if numbers x and y are == or are within (* n-ulps ulp) of 
  each other, where ulp is the minimum of (Math/ulp x) and (Math/ulp y).
  A ulp is \"units in the last place\", i.e. the minimum possible difference
  between two floating point numbers, but the numeric value of a ulp differs
  depending on the number, even within the same numeric class such as double.
  We use the minimum since that's the least difference between one of the
  numbers and the next one up or down from  it.  (It seem as if multiplying a
  number that's one ulp off produces a number that is some power of 2 ulp's
  away from the correct value.) See java.lang.Math for more."
  [n-ulps x y]
  (or (== x y)
      (let [xd (double x) ; Math/ulp doesn't work on integers
            yd (double y)
            ulp (min (Math/ulp xd) (Math/ulp yd))]
        (<= (abs (- xd yd))
            (* n-ulps ulp)))))

(defn rotate
  "Given an angle theta and a pair of coordinates [x y], returns a
  new pair of coordinates that is the rotation of [x y] by theta."
  [theta [x y]]
  [(- (* x (cos theta))
      (* y (sin theta))) ,
   (+ (* y (cos theta))
      (* x (sin theta)))])

(defn distance-2D
  "Computes distance between two-dimensional points [x0 y0] and [x1 y1]
  using the Pythagorean theorem."
  [[x0 y0] [x1 y1]]
  (let [xdiff (- x0 x1)
        ydiff (- y0 y1)]
  (nt/sqrt (+ (* xdiff xdiff) (* ydiff ydiff)))))

;; Implements $x = \frac{-b \pm \sqrt{b^2 - 4ac}}{2a}\;$  given $\;ax^2 + bx + c = 0$.
;; (If both results are routinely needed inside a tight loop, consider making 
;; a version of this function that returns both of them.)
(defn quadratic-formula
  "Returns the result of the quadratic formula applied to the coefficients in
  ax^2 + bx + c = 0.  plus-or-minus should be one of the two functions: + - ."
  [plus-or-minus a b c]
  (let [root-part (nt/sqrt (- (* b b) (* 4 a c)))
        negb (- b)
        a2 (* 2 a)]
    (/ (plus-or-minus negb root-part) a2)))


(defn mean
  "Returns the mean value of all numbers in collection xs, or the
  first n values if n is provided.  If n greater than the length of xs,
  takes the mean of xs."
  ([xs]
   (let [n (count xs)]
     (/ (reduce + xs) n)))
  ([n xs] (mean (take n xs)))) ; don't divide by n explicitly: xs may be short

(defn count-decimal-digits
  "Given a number, returns the number of digits in the decimal
  representation of its integer part."
  [n]
  (count (str (nt/round n))))

;;;;;;;;;;;;;;;;;;;;;;;

(comment
  ;; USE APACHE COMMONS PARETO DISTRIBUTION INSTEAD:

  ;; Pareto PDF: $\mathsf{P}(x) = \frac{\alpha x_m^{\alpha}}{x^{\alpha + 1}}$, again for $x \leq x_m$.
  ;; (Note that memoizing this makes it slower.  Rearranging to use expt only
  ;; once also makes it slower.)
  (defn pareto
    "Given a scale parameter x_m (min value, should be positive) and a shape parameter
    alpha (positive), returns the value of the Pareto density function at x
    (from https://en.wikipedia.org/wiki/Pareto_distribution).  Returns 0
    if x < minumum"
    [xm alpha x]
    (if (< x xm)
      0
      (/ (* alpha (nt/expt xm alpha))
         (nt/expt x (inc alpha)))))

  ; Assuming that $\mu > 1$, 
  ; $\int_r^{\infty} x^{-\mu} \; dl = \frac{r^{1-\mu}}{\mu-1} \,$.
  ; &nbsp; So to distribute step lengths $x$ as $x^{-\mu}$ with $r$ as 
  ; the minimum length,
  ; $\mathsf{P}(x) = x^{-\mu}\frac{\mu-1}{r^{1-\mu}} = x^{-\mu}r^{\mu-1}(\mu-1)$.
  ;; &nbsp; See steplengths.md for further details.  &nbsp; cf. Viswanathan et al., *Nature* 1999.
  ;; This can be viewed as a Pareto distribution, but parameterized differently.
  (defn powerlaw
    "Returns probability of x with normalized density x^mu, where r is
    x's minimum value.  Returns 0 if x < minumum."
    [r mu x]
    (if (< x r)
      0
      (let [mu- (dec mu)]
        (* (nt/expt x (- mu)) (nt/expt r mu-) mu-))))
)
