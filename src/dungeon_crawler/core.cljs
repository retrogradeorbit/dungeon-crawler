(ns dungeon-crawler.core
  (:require  [infinitelives.pixi.canvas :as c]
             [infinitelives.pixi.resources :as r]
             [infinitelives.pixi.tilemap :as tm]
             [infinitelives.pixi.texture :as t]
             [infinitelives.pixi.sprite :as s]
             [infinitelives.utils.events :as e]
             [infinitelives.utils.vec2 :as vec2]
             [infinitelives.utils.gamepad :as gp]
             [infinitelives.utils.console :refer [log]]

             [dungeon-crawler.line :as line])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [infinitelives.pixi.macros :as m]))

(enable-console-print!)

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

(defonce canvas
  (c/init {:layers [:bg :tilemap :ui]
           :background bg-colour
           :expand true}))

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
                                    :xhandle 0 :yhandle 0
                                    :particle-opts #{:uvs})
          player (s/make-sprite :down-1
                                :scale scale
                                :x 0 :y 0)
          ]
      (m/with-sprite :tilemap
        [
                                        ;tile-map-sprite tile-map
                                        ;player-sprite player
         container (s/make-container :children [tile-map player]
                                     :scale 3)
         ]

        ;; door opens and closes
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
            (tm/alter-tile! tile-sprites [1 3] tile-set :door-left-4)))

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

                new-pos (vec2/add pos vel)

                _ (log pos "->" new-pos)

                new-pos (line/constrain
                         {:passable? (fn [x y]
                                       (let [x (/ x 16)
                                             y (/ y 16)]
                                        ;(log x y (get-in level-map [y x]))
                                         (#{:floor :floor-2 :floor-3 :floor-4}
                                          (get-in level-map [y x]))))
                          :h-edge 0.3
                          :v-edge 0.3
                          :minus-h-edge 0.7
                          :minus-v-edge 0.7}
                         new-pos pos)]
            (log "new pos is now:" new-pos)
            (s/set-pos! player new-pos)

            (case (vec2/get-x joy)
              -1
              (do (s/set-texture! player :right-1)
                  (s/set-scale! player (- scale) scale))
              1
              (do (s/set-texture! player :right-1)
                  (s/set-scale! player scale scale))
              nil
              )

            (case (vec2/get-y joy)
              -1
              (do (s/set-texture! player :up-1)
                  (s/set-scale! player scale))
              1
              (do (s/set-texture! player :down-1)
                  (s/set-scale! player scale))
              nil
              )



            (<! (e/next-frame))
            (recur new-pos
             (-> vel
                 (vec2/add joy)
                 (vec2/scale 0.90)
                 (vec2/truncate 1)))))

        ))))
