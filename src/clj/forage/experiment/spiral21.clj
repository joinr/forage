;; spiral21.clj
;; Copied from spiral20.clj and modified.
(ns forage.experiment.spiral21
  (:require [forage.run :as fr]
            [forage.food :as f]
            [forage.walks :as w]
            [utils.spiral :as sp]
            [forage.env-mason :as em]
            [utils.random :as r]))

(def default-dirname "../../data.foraging/forage/")

;; FOOD-DISTANCE SHOULD DIVIDE HALF-SIZE EVENLY, OR THERE WON't BE FOOD AT CENTER,
;; WHICH IS WHERE THE SEARCH STARTS.
(def half-size  500000) ; half the full width of the env
(def maxpathlen (* 10 half-size)) ; max length of an entire continuous search path
(def explore-segment-len (/ maxpathlen 20.0)) ; max length of walk segments that go far
(def examine-segment-len (/ maxpathlen 5.0))  ; max length of walk segments that stay local (not exploit, but rather "look closely", examine)
(def trunclen explore-segment-len)
(def food-distance nil) ; won't be used

;; Initial default params, with:
;; (a) Search starts in a random initial direction
;; (b) Search starts exactly from init-loc (e.g. for destructive search)
(def params (sorted-map ; sort so labels match values
             :food-distance       food-distance 
             :perc-radius         1  ; distance that an animal can "see" in searching for food
             :powerlaw-min        1
             :env-size            (* 2 half-size)
             :env-discretization  5 ; for Continuous2D; see foodspot.clj
             :init-loc-fn         (constantly [half-size half-size]) ; TODO MODIFY THIS?
             :init-pad            nil ; if truthy, initial loc offset by this in rand dir
             :maxpathlen          maxpathlen
             :trunclen            trunclen
             :look-eps            0.2    ; TODO WILL THIS WORK WITH SHORTER SPIRAL SEGMENTS?
             :basename            (str default-dirname "spiral21_")
             ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; SINGLE-FOODSPOT ENV

(defn make-single-target-env
  "Make an env with a single foodspot between the center of the core env
  and the right edge along the equator."
  [denom nomin]
  (let [half-size (/ (params :env-size) 2)] ; probably already defined as var, but should get from params
    (em/make-env (params :env-discretization)
                 (params :env-size)
                 [[(long (+ half-size (* (/ nomin denom) half-size))) ; coerce to long: avoid probs later with Ratio, BigInt
                   half-size]])))

;; Make envs each with a single target but at several different distances
;; from center as proportion of size of env:
(def envs (mapv (partial make-single-target-env 5)
                (range 1 5))) ; four targets at 1/5, 2/4, 3/4, 4/5 of distance to border

(comment
  (count envs)
)

(defn make-toroidal-look-fn
  [env]
  (partial em/perc-foodspots-exactly-toroidal env (params :perc-radius)))

(defn make-unbounded-look-fn
  "Make a non-toroidal look-fn from env.  Searches that leave the core env
  will just continue without success unless they wander back."
  [env]
  (partial em/perc-foodspots-exactly env (params :perc-radius)))



(comment
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ;; OK, LET'S TRY SOME SAMPLE SEARCHES THAT ARE MORE LIKE WHAT I WANT:
  (def seed (r/make-seed))
  (def seed 6097574690509144268)
  (def rng (r/make-well19937 seed))
  (def mu1xdist (r/make-powerlaw rng 1 1.25))
  ;(def mu2dist (r/make-powerlaw rng 1 2))
  (def mu3dist (r/make-powerlaw rng 1 3))

  (defn more-mu1x-vecs [] 
    (w/vecs-upto-len explore-segment-len (w/make-levy-vecs rng mu1xdist 1 (params :trunclen))))
  (defn more-mu3-vecs [] 
    (w/vecs-upto-len  examine-segment-len (w/make-levy-vecs rng mu3dist  1 (params :trunclen))))
  (defn more-spiral-vecs []
    (w/vecs-upto-len  examine-segment-len (sp/unit-archimedean-spiral-vecs 2 0.1)))

  ;; Not sure if I'm violating this advice, or if the number of iterations
  ;; are such that I can blow the stack, or what the best way to avoid this is.
  ;; https://stuartsierra.com/2015/04/26/clojure-donts-concat
  ;; SEE spiral20.clj FOR OTHER WAYS.
  (defn composite-mu1-mu3-vecs
    [maxpathlen]
    (w/vecs-upto-len maxpathlen
                     (apply concat
                            (repeatedly #(into [] cat [(more-mu1x-vecs) (more-mu3-vecs)])))))
  (defn composite-mu1-spiral-vecs
    [maxpathlen]
    (w/vecs-upto-len maxpathlen
                     (apply concat
                            (repeatedly #(into [] cat [(more-mu1x-vecs) (more-spiral-vecs)])))))


  (def walk-fns
      {"composite-brownian-env0" (fn [init-loc] (w/foodwalk (make-unbounded-look-fn (envs 0)) (params :look-eps) (w/walk-stops init-loc (composite-mu1-mu3-vecs (params :maxpathlen)))))
       "composite-brownian-env1" (fn [init-loc] (w/foodwalk (make-unbounded-look-fn (envs 1)) (params :look-eps) (w/walk-stops init-loc (composite-mu1-mu3-vecs (params :maxpathlen)))))
       "composite-brownian-env2" (fn [init-loc] (w/foodwalk (make-unbounded-look-fn (envs 2)) (params :look-eps) (w/walk-stops init-loc (composite-mu1-mu3-vecs (params :maxpathlen)))))
       "composite-brownian-env3" (fn [init-loc] (w/foodwalk (make-unbounded-look-fn (envs 3)) (params :look-eps) (w/walk-stops init-loc (composite-mu1-mu3-vecs (params :maxpathlen)))))

       "composite-spiral-env0"   (fn [init-loc] (w/foodwalk (make-unbounded-look-fn (envs 0)) (params :look-eps) (w/walk-stops init-loc (composite-mu1-spiral-vecs (params :maxpathlen)))))
       "composite-spiral-env1"   (fn [init-loc] (w/foodwalk (make-unbounded-look-fn (envs 1)) (params :look-eps) (w/walk-stops init-loc (composite-mu1-spiral-vecs (params :maxpathlen)))))
       "composite-spiral-env2"   (fn [init-loc] (w/foodwalk (make-unbounded-look-fn (envs 2)) (params :look-eps) (w/walk-stops init-loc (composite-mu1-spiral-vecs (params :maxpathlen)))))
       "composite-spiral-env3"   (fn [init-loc] (w/foodwalk (make-unbounded-look-fn (envs 3)) (params :look-eps) (w/walk-stops init-loc (composite-mu1-spiral-vecs (params :maxpathlen)))))

       "mu2-env0" (partial fr/levy-run rng (make-unbounded-look-fn (envs 0)) nil params 2.0)
       "mu2-env1" (partial fr/levy-run rng (make-unbounded-look-fn (envs 1)) nil params 2.0)
       "mu2-env2" (partial fr/levy-run rng (make-unbounded-look-fn (envs 2)) nil params 2.0)
       "mu3-env3" (partial fr/levy-run rng (make-unbounded-look-fn (envs 3)) nil params 2.0)})

  (def data-and-rng (time (fr/walk-experiments params walk-fns 1000 seed)))


  ;; What do these walks look like?
  (require '[forage.viz.hanami :as h])
  (require '[oz.core :as oz])
  (oz/start-server!)
  (def walk (w/walk-stops [half-size half-size] (composite-mu1-mu3-vecs (params :maxpathlen))))
  (def walk (w/walk-stops [half-size half-size] (composite-mu1-spiral-vecs (params :maxpathlen))))
  (oz/view! (h/vega-envwalk-plot (envs 0) 600 1.0 1000 walk))

  (def vl-walk (h/order-walk-with-labels "walk " walk))
  (def plot (h/vega-walk-plot 600 2000 1.0 vl-walk))
  (oz/view! plot)

)
