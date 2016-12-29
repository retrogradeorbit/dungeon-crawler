(ns dungeon-crawler.core
  (:require  [infinitelives.pixi.canvas :as c]
             [infinitelives.pixi.resources :as r]
             [infinitelives.pixi.tilemap :as tm]
             [infinitelives.pixi.texture :as t]
             [infinitelives.pixi.sprite :as s]
             [infinitelives.utils.events :as e]
             [infinitelives.utils.vec2 :as vec2]
             [infinitelives.utils.gamepad :as gp]
             [infinitelives.utils.pathfind :as path]
             [infinitelives.utils.console :refer [log]]

             [cljs.core.async :refer [chan put!]]
             [cljs.core.match :refer [match]]

             [dungeon-crawler.line :as line])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [infinitelives.pixi.macros :as m]))

(enable-console-print!)

(def passable-tile-set
  #{:floor :floor-2 :floor-3 :floor-4
    :door-bottom-1 :door-bottom-2
    :door-left-2 :door-left-4
    :door-bottom-left :door-bottom-right})

(defonce bg-colour 0x0D0711)

(def scale 1)

(def tile-map-chars
  [
   " ┌──────╖          "
   " │o...oo║          "
   "<>.,....║   -=     "
   "#$.....:╙───{}╖    "
   " │o..,.....,..║    "
   " │o....o╔═══╕.║    "
   " │:o..oo║   │.║    "
   " ╘()════╝   │o║    "
   "  []   -=   │.║    "
   "     ┌─{}───┘.╙╖   "
   "     │oo..,...o║   "
   "    !@..:......║   "
   "    AB,.....o..║   "
   "     ╘═════════╝   "])

(def room2-map-chars
  [
   "         -=         "
   "┌────────{}────────╖"
   "│.o.o.o.o.o.o.o.o.o║"
   "│o.o.o.o.o.o.o.o.o.║"
   "╘()══════════════()╝"
   " []              [] "
   ])

(def key-for
  {
   "┌" :wall-top-left
   "╖" :wall-top-right
   "╘" :wall-bottom-left
   "╝" :wall-bottom-right
   "─" :wall-top
   "│" :wall-left
   "║" :wall-right
   "═" :wall-bottom
   "╔" :wall-top-left-outer
   "╙" :wall-bottom-left-outer
   "╕" :wall-top-right-outer
   "┘" :wall-bottom-right-outer
   "-" :door-top-left
   "=" :door-top-right
   "{" :door-bottom-left
   "}" :door-bottom-right
   "(" :door-bottom-1
   ")" :door-bottom-2
   "[" :door-bottom-3
   "]" :door-bottom-4
   "<" :door-left-1
   ">" :door-left-2
   "#" :door-left-3
   "$" :door-left-4
   "!" :door-left-shut-1
   "@" :door-left-shut-2
   "A" :door-left-shut-3
   "B" :door-left-shut-4
   "." :floor
   "," :floor-2
   "o" :floor-3
   ":" :floor-4
   " " nil})

(def tile-set-mapping
  {
   :wall-top-left [48 0]
   :wall-top-right [64 0]
   :wall-bottom-left [48 16]
   :wall-bottom-right [64 16]
   :wall-top [16 32]
   :wall-left [32 16]
   :wall-right [0 16]
   :wall-bottom [16 0]
   :wall-top-left-outer [0 0]
   :wall-bottom-left-outer [0 32]
   :wall-top-right-outer [32 0]
   :wall-bottom-right-outer [32 32]
   :floor [96 0]
   :floor-2 [112 0]
   :floor-3 [128 32]
   :floor-4 [128 0]
   :door-top-left [32 80]
   :door-top-right [48 80]
   :door-bottom-left [32 96]
   :door-bottom-right [48 96]
   :door-bottom-1 [112 112]
   :door-bottom-2 [128 112]
   :door-bottom-3 [112 128]
   :door-bottom-4 [128 128]
   :door-left-1 [144 144]
   :door-left-2 [160 144]
   :door-left-3 [144 160]
   :door-left-4 [160 160]
   :door-left-shut-1 [144 176]
   :door-left-shut-2 [160 176]
   :door-left-shut-3 [144 192]
   :door-left-shut-4 [160 192]

   :door-top-overlay-1 [288 0]
   :door-top-overlay-2 [288 16]
   :door-top-overlay-3 [304 0]
   :door-top-overlay-4 [304 16]

   :door-bottom-overlay-1 [288 32]
   :door-bottom-overlay-2 [288 48]
   :door-bottom-overlay-3 [304 32]
   :door-bottom-overlay-4 [304 48]

   :door-right-overlay-1 [288 64]
   :door-right-overlay-2 [288 80]
   :door-right-overlay-3 [304 64]
   :door-right-overlay-4 [304 80]

   :door-left-overlay-1 [288 96]
   :door-left-overlay-2 [288 112]
   :door-left-overlay-3 [304 96]
   :door-left-overlay-4 [304 112]


})

(def hero
  {:up-1 {:pos [0 0] :size [16 16]}
   :up-2 {:pos [16 0] :size [16 16]}
   :up-3 {:pos [32 0] :size [16 16]}
   :up-4 {:pos [48 0] :size [16 16]}

   :right-1 {:pos [0 16] :size [16 16]}
   :right-2 {:pos [16 16] :size [16 16]}
   :right-3 {:pos [32 16] :size [16 16]}
   :right-4 {:pos [48 16] :size [16 16]}

   :down-1 {:pos [0 32] :size [16 16]}
   :down-2 {:pos [16 32] :size [16 16]}
   :down-3 {:pos [32 32] :size [16 16]}
   :down-4 {:pos [48 32] :size [16 16]}

   :up-action {:pos [0 48] :size [16 16]}
   :right-action {:pos [16 48] :size [16 16]}
   :down-action {:pos [32 48] :size [16 16]}})

(def tile-to-overlay-map
  {:door-top-left :door-top-overlay-1
   :door-top-right :door-top-overlay-3
   :door-bottom-left :door-top-overlay-2
   :door-bottom-right :door-top-overlay-4
   :door-bottom-1 :door-bottom-overlay-1
   :door-bottom-2 :door-bottom-overlay-3
   :door-bottom-3 :door-bottom-overlay-2
   :door-bottom-4 :door-bottom-overlay-4
   :door-left-1 :door-left-overlay-1
   :door-left-2 :door-left-overlay-3
   :door-left-3 :door-left-overlay-2
   :door-left-4 :door-left-overlay-4
   :door-right-1 :door-right-overlay-1
   :door-right-2 :door-right-overlay-3
   :door-right-3 :door-right-overlay-2
   :door-right-4 :door-right-overlay-4})

(defonce canvas
  (c/init {:layers [:bg :tilemap :ui]
           :background bg-colour
           :expand true}))

(defonce state (atom {:pos (vec2/zero)
                      :walk-to nil}))
(defn int-vec [v]
  (mapv int v))

(defn door-horiz-constrain-passable? [x y]
  (not
   (or
    (and (= x 1) (= y 7))
    (and (= x 4) (= y 7))
    (and (= x 11) (= y 3))
    (and (= x 14) (= y 3))
    (and (= x 6) (= y 9))
    (and (= x 9) (= y 9)))))

(defn door-vert-constrain-passable? [x y]
  (not
   (or
    (and (= x 1) (= y 1))
    (and (= x 1) (= y 4))
    (and (= x 5) (= y 10))
    (and (= x 5) (= y 13)))))

(defn door-opens-and-closes [tile-sprites tile-set]
  (go
    (while true
      ;; closed
      (<! (e/wait-frames 120))
      (tm/alter-tile! tile-sprites [0 2] tile-set :door-left-shut-1)
      (tm/alter-tile! tile-sprites [1 2] tile-set :door-left-shut-2)
      (tm/alter-tile! tile-sprites [0 3] tile-set :door-left-shut-3)
      (tm/alter-tile! tile-sprites [1 3] tile-set :door-left-shut-4)

      ;; open
      (<! (e/wait-frames 120))
      (tm/alter-tile! tile-sprites [0 2] tile-set :door-left-1)
      (tm/alter-tile! tile-sprites [1 2] tile-set :door-left-2)
      (tm/alter-tile! tile-sprites [0 3] tile-set :door-left-3)
      (tm/alter-tile! tile-sprites [1 3] tile-set :door-left-4))))

(defonce main
  (go
    ;; load resource url with tile sheet
    (<! (r/load-resources canvas :ui ["img/tiles.png"
                                      "img/notlink.png"]))
    (t/load-sprite-sheet!
     (r/get-texture :notlink :nearest)
     hero)

    (let [tile-set (tm/make-tile-set :tiles tile-set-mapping [16 16])
          level-map (->> tile-map-chars
                         (tm/make-tile-map key-for))
          tile-sprites (tm/make-tile-sprites tile-set level-map)
          tile-map (tm/make-tilemap tile-sprites
                                    :scale scale
                                    ;:alpha 0.2
                                    :xhandle 0 :yhandle 0
                                    :particle-opts #{:uvs})
          room2-map (->> room2-map-chars
                         (tm/make-tile-map key-for))
          room2-sprites (tm/make-tile-sprites tile-set room2-map)
          room2-tile-map (tm/make-tilemap room2-sprites
                                          :scale scale
                                          :x (* 16 -5)
                                          :y (* 16 -2)
                                          :alpha 0.0
                                          :xhandle 0 :yhandle 0
                                          :particle-opts #{:uvs})
          room2-overlay-map (into [] (for [row room2-map]
                                 (into []
                                       (for [c row]
                                         (tile-to-overlay-map c)))))
          room2-overlay-sprites (tm/make-tile-sprites tile-set room2-overlay-map)
          room2-overlay (tm/make-tilemap room2-overlay-sprites
                                   :scale scale
                                   :x (* 16 -4)
                                   :y (* 16 -2)
                                   :alpha 0.0
                                   :xhandle 0 :yhandle 0
                                   :particle-opts #{:uvs})

          overlay-map (into [] (for [row level-map]
                                 (into []
                                       (for [c row]
                                         (tile-to-overlay-map c)))))
          overlay-sprites (tm/make-tile-sprites tile-set overlay-map)
          _ (js/console.log (str overlay-map))
          overlay (tm/make-tilemap overlay-sprites
                                   :scale scale
                                   :x (* 16 0)
                                   :y (* 16 2)
                                   :alpha 1.0
                                   :xhandle 0 :yhandle 0
                                   :particle-opts #{:uvs})

          player (s/make-sprite :down-1
                                :scale scale
                                :x 0 :y 0)
          walk-to-chan (chan)
          mousedown (fn [ev]
                      (put! walk-to-chan
                            (let [[x y] (s/container-transform tile-map (.-data.global ev))
                                  x (int (/ x 16))
                                  y (int (/ y 16))]
                              [x y])))
          ]
      (set! (.-hitArea tile-map) (new js/PIXI.Rectangle 0 0 1000 1000))
      (set! (.-interactive tile-map) true)

      #_ (set! (.-mousedown tile-map) (fn [ev] (put! walk-to-chan (let [[x y] (s/container-transform tile-map (.-data.global ev))
                                                                        x (int (/ x 16))
                                                                        y (int (/ y 16))]
                                                                    [x y]) )))
      (m/with-sprite :tilemap
        [
                                        ;tile-map-sprite tile-map
                                        ;player-sprite player
         container (s/make-container
                    :children [tile-map room2-tile-map player overlay room2-overlay]
                    :mousedown mousedown
                    :touchstart mousedown
                    :scale 3)
         ]

        ;; camera tracking
        (go
          (loop [cam (:pos @state)]
            (let [[cx cy] (vec2/get-xy cam)]

              (s/set-pivot! container (/ (int (* 3 cx)) 3) (/ (int (* 3 cy)) 3) ;(int cx) (int cy)
                            )
              (<! (e/next-frame))
              (let [next-pos (:pos @state)
                    v (vec2/sub next-pos cam)
                    mag (vec2/magnitude-squared v)]
                (recur (vec2/add cam (vec2/scale v (* 0.00001 mag))))))))

        ;; level change opacity as we pass through doors
        (go
          (while true
            (let [pos (vec2/scale (:pos @state) (/ 1 16))
                  [x y] (vec2/get-xy pos)
                  xi (int x)
                  yi (int y)]
              ;; each doors
              (cond
                (and (or (= xi 12) (= xi 13)) (= yi 3))
                (do ;(js/console.log "upper right" (- y yi))
                    (s/set-alpha! tile-map (- y yi))
                    (s/set-alpha! room2-tile-map (- 1 (- y yi)))
                    (s/set-alpha! room2-overlay (- 1 (- y yi)))
                    (s/set-alpha! overlay (- y yi))
                    (s/set-pos! room2-tile-map (* 16 -5) (* 16 -2) )
                    (s/set-pos! room2-overlay (* 16 -4) (* 16 -2) )
                    )

                (and (or (= xi 7) (= xi 8)) (= yi 9))
                (do ;(js/console.log "lower right" (- y yi))
                    (s/set-alpha! tile-map (- y yi))
                    (s/set-alpha! room2-tile-map (- 1 (- y yi)))
                    (s/set-alpha! room2-overlay (- 1 (- y yi)))
                    (s/set-alpha! overlay (- y yi))
                    (s/set-pos! room2-tile-map (* 16 6) (* 16 4) )
                    (s/set-pos! room2-overlay (* 16 7) (* 16 4) )
                    )

                (and (or (= xi 2) (= xi 3)) (= yi 7))
                (do ;(js/console.log "lower left" (- y yi))
                    (s/set-alpha! tile-map (- 1 (- y yi)))
                    (s/set-alpha! room2-tile-map (- y yi))
                    (s/set-alpha! room2-overlay (- y yi))
                    (s/set-alpha! overlay (- 1 (- y yi)))
                    (s/set-pos! room2-tile-map (* 16 -7) (* 16 7) )
                    (s/set-pos! room2-overlay (* 16 -6) (* 16 7) )
                    )

                :default
                (do ;(js/console.log "none" xi yi)
                    (s/set-alpha! tile-map 1.0)
                    (s/set-alpha! room2-tile-map 0.0)
                    (s/set-alpha! room2-overlay 0.0)

                     (s/set-alpha! overlay 1.0)
                     ))

              )

            (<! (e/next-frame))))

        ;; door opens and closes
        (door-opens-and-closes tile-sprites tile-set)

        ;; walk to input
        (go
          (while true
            (let [passable?
                  (fn [[x y]]
                    (boolean (passable-tile-set
                              (get-in level-map [y x]))))

                  dest (<! walk-to-chan)

                  [xp yp] (vec2/as-vector
                           (vec2/scale (:pos @state) (/ 1 16)))

                  path (path/A* passable? [(int xp) (int yp)]
                                dest)
                  ]
              (when path
                ;; play out the path into walk-to state
                ;; from start destination to last
                (loop [[n & r] path]
                  (swap! state assoc :walk-to n)
                  (while
                      (let [[xd yd] (int-vec n)
                            [xp yp] (int-vec (vec2/as-vector
                                              (vec2/scale (:pos @state) (/ 1 16))))]
                        (or (not= xp xd) (not= yp yd)))
                    (<! (e/next-frame)))
                  (when (seq r) (recur r)))
                (swap! state assoc :walk-to nil)))))

        (loop [pos (vec2/vec2 50 50)
               vel (vec2/zero)]
          (let [
                joy (vec2/vec2 (or (gp/axis 0)
                                   (cond (e/is-pressed? :left) -1
                                         (e/is-pressed? :right) 1
                                         :default 0) )
                               (or (gp/axis 1)
                                   (cond (e/is-pressed? :up) -1
                                         (e/is-pressed? :down) 1
                                         :default 0)
                                   ))

                walk-to (:walk-to @state)
                [wtx wty] walk-to
                walk-to-delta (if (nil? walk-to)
                                (vec2/zero)
                                (vec2/sub
                                 (vec2/vec2 (* 16 (+ 0.5 wtx))
                                            (* 16 (+ 0.5 wty)))
                                 pos))

                new-vel-a (-> vel
                              (vec2/add (vec2/scale joy .3))
                              (vec2/add (vec2/scale walk-to-delta 0.1))
                              (vec2/scale 0.95)
                              (vec2/truncate 2))




                new-pos-a (vec2/add pos new-vel-a)

                ;; constrain for walls
                c-pos (line/constrain
                       {:passable? (fn [x y]
                                     (passable-tile-set
                                      (get-in level-map [y x])))
                        :h-edge 0.01
                        :v-edge 0.01
                        :minus-h-edge 0.99
                        :minus-v-edge 0.99}
                       (vec2/scale pos (/ 1 16))
                       (vec2/scale new-pos-a (/ 1 16)))

                c-pos (line/constrain
                       {:passable? door-horiz-constrain-passable?
                        :h-edge 0.75
                        :v-edge 0.01
                        :minus-h-edge 0.25
                        :minus-v-edge 0.99}
                       (vec2/scale pos (/ 1 16))
                       c-pos)

                c-pos (line/constrain
                       {:passable? door-vert-constrain-passable?
                        :h-edge 0.01
                        :v-edge 0.75
                        :minus-h-edge 0.99
                        :minus-v-edge 0.25}
                       (vec2/scale pos (/ 1 16))
                       c-pos)

                new-pos (vec2/scale c-pos 16)
                new-vel (vec2/sub new-pos pos)
                ]
                                        ;(log "->" new-pos-a new-pos new-vel-a new-vel)

            (swap! state assoc :pos new-pos)
            (let [[xp yp] (vec2/get-xy new-pos)]
              (s/set-pos! player xp yp))

            (case (vec2/direction-quad new-vel)
              :left
              (do (s/set-texture! player :right-1)
                  (s/set-scale! player (- scale) scale))
              :right
              (do (s/set-texture! player :right-1)
                  (s/set-scale! player scale scale))
              :up
              (do (s/set-texture! player :up-1)
                  (s/set-scale! player scale))
              :down
              (do (s/set-texture! player :down-1)
                  (s/set-scale! player scale)))

            (<! (e/next-frame))
            (recur new-pos new-vel)))))))
