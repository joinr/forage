;; Functions for plotting things in 2D spatial coordinates using cljplot
(ns forage.viz.cljplot
  (:require
   [forage.toroidal :as t]  ; temporary for testing in three-plots. remove when possible.
   [cljplot.build :as cb]
   [cljplot.core :as cc]
   [cljplot.render :as cr]
   ;[clojure2d.core :refer [get-image canvas show-window with-canvas-> width height image]]
   [clojure2d.core :as c2] ; how is this working without including clojure2d as a dep in project.clj??
   ))


;; Moved to toroidal.clj
;(defn add-cljplot-path-breaks
;  "Given a path generated by toroidal/wrap-path, in which \"duplicated\"
;  sequences of points are separated by nils, replaces the nils with
;  cljplot separators [#NaN ##NaN]."
;  [pts]
;  (replace {nil [##NaN ##NaN]} pts))


;; TODO Oh wait--can I use the verson of this in latest dev version of clojure2d
;; By generateme, from 
;; https://clojurians.zulipchat.com/#narrow/stream/197967-cljplot-dev/topic/Options.20for.20displaying.20a.20plot.3F/near/289959227
(defn show-image
  "Show image" ; FIXME
  ([img] (show-image img {}))
  ([img args]
   (let [img (c2/get-image img)
         c (c2/canvas (c2/width img) (c2/height img))]
     (c2/with-canvas-> c
       (c2/image img))
     (let [w (c2/show-window (merge args {:canvas c}))]
       (when-let [[x y] (:position args)]
         (.setLocation (:frame w) (int x) (int y)))
       w))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Functions below are not in final form, and might not exist in the
;; future.  They're primarily for testing.


;; TODO Add foodspots
;; Based on https://clojurians.zulipchat.com/#narrow/stream/197967-cljplot-dev/topic/periodic.20boundary.20conditions.2Ftoroidal.20world.3F/near/288501054
;; Notes on cljplot usage:
;;    in arg to cb/series :
;;    [:grid] selects a default grid pattern
;;    [:grid nil] seems to be the same; the first nil below seems to be an argument placeholder
;;    [:grid nil {:x nil}] means that there are no vertical grid lines
;;    [:grid nil {:y nil}] means that there are no horizontal grid lines
;; Fourth element in the :color option seems to be transparency or darkness or something
;;    [:grid nil {:position [0 1]}] I don't understand; squashes plot somewhere other than gridlines
(defn plot-walk
  "display-bound-min and display-bound-max determine, respectively, the
  lower and left boundaries of the image, and the upper and right 
  boundaries of the image.  If only display-bound is given, it will be used
  for both.  data-bound-min, data-bound-max, and data-bound work the same
  way to determine the standard region (see doc/ToroidalAlgorithms.md).
  data is a sequence of points (pairs of coordinates) to be plotted as
  a walk. If filename is present, it should be a string that will be the
  name of a jpeg file for the plot.  If absent, a Clojure2D plot window
  will be generated.  (As you can see, this can only be done with the simple
  argument form.)  The walk is plotted as is, except that box is drawn where
  the region boundaries are."
  ([display-bound data-bound data]
   (plot-walk display-bound data-bound data nil))
  ([display-bound data-bound data filename]
   (plot-walk (- display-bound) display-bound
                (- data-bound) data-bound data filename))
  ([display-bound-min display-bound-max
    data-bound-min data-bound-max ; only used to make box
    data filename]
   (let [box-segs [[data-bound-min data-bound-min]
                   [data-bound-min data-bound-max]
                   [data-bound-max data-bound-max]
                   [data-bound-max data-bound-min]
                   [data-bound-min data-bound-min]
                   [##NaN ##NaN]]
         plotfn (fn [chart] (if filename
                              (cc/save chart filename)
                              ;(cc/show chart)
                              (show-image  chart)
                              ))]
     (-> (cb/series [:grid] [:line (concat box-segs (t/toroidal-to-cljplot data))
                             {:color [0 0 255 150] ; fourth arg is opacity or brightness or something like that
                              :margins nil}]) 
         (cb/preprocess-series)
         (cb/update-scale :x :domain [display-bound-min display-bound-max])
         (cb/update-scale :y :domain [display-bound-min display-bound-max])
         (cb/add-axes :bottom)
         (cb/add-axes :left)
         (cr/render-lattice {:width 800 :height 800 :border 10})
         (plotfn)))))


(defn three-plots
  "Convenience function for testing.  Runs plot-walk three times
  on the same data (a sequence of points), generating three walk
  plot files named:
  \"unmod.jpg\": Plot original data, unmodified.
  \"loose.jpg\": Plot data after passing it through wrap-path, but
  with a large image boundary.
  \"tight.jpg\": Plot data after passing it through wrap-path, but
  with image boundary almost equal to the region boundary.
  outer is the amount added to [0,0] in all four directions to determine
  the image boundary. inner does the same thing for the standard region
  boundary (see doc/ToroidalAlgorithms.md). inner- and inner+ instead
  specify, respectively, the lower and left region boundaries, and the 
  upper and right region boundaries."
  ([outer inner data] (three-plots outer (- inner) inner data))
  ([outer inner- inner+ data]
   (plot-walk (- outer) outer inner- inner+ data "unmod.jpg")
   (plot-walk (- outer) outer inner- inner+ (t/wrap-path inner- inner+ data) "loose.jpg")
   ;(plot-walk (* 1.02 inner-) (* 1.02 inner+)
   (plot-walk inner- inner+
              inner- inner+
              (t/wrap-path inner- inner+ data) "tight.jpg")))
