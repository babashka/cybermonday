(ns cybermonday.core
  (:require
   [cybermonday.lowering :refer [to-html-hiccup default-lowering]]
   [cybermonday.ir :refer [md-to-ir]]
   #?(:clj [clj-yaml.core :as yaml]
      :cljs ["yaml" :as yaml])))

(def frontmatter-re #"(?ms)(?:^---$(.*)^---$)?(.*)")

(def parse-yaml #?(:clj yaml/parse-string
                   :cljs yaml/parse))

(defn parse-md
  "Generates HTML hiccup from markdown and associated frontmatter"
  ([md lowering-map]
   (let [[_ fm body] (re-matches frontmatter-re md)]
     {:frontmatter (when fm (parse-yaml fm))
      :body (-> body
                md-to-ir
                (to-html-hiccup lowering-map))}))
  ([md] (parse-md md default-lowering)))

(def test-str "![][image_ref_a32ff4ads]

text

[image_ref_a32ff4ads]: data:image/png;base64,iVBORw0KGgoAAAANSUhEke02C1MyA29UWKgPA...RS12D==")

(parse-md test-str)
