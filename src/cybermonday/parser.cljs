(ns cybermonday.parser
  (:require
   [cybermonday.utils :refer [make-hiccup-node html-comment-re]]
   ["remark" :as remark]
   ["unified" :as unified]
   ["remark-math" :as math]
   ["remark-parse" :as rp]
   ["remark-footnotes" :as footnotes]
   ["html-entities" :as entities]
   ["remark-gfm" :as gfm]))

(def parser (.. (unified)
                (use rp)
                (use footnotes)
                (use math)
                (use gfm)))

(def node-tags
  "The default mapping from Flexmark AST node to Hiccup tag"
  {"root" :div
   "paragraph" :p
   "emphasis" :em
   "thematicBreak" :hr
   "strong" :strong
   "blockquote" :blockquote
   "listItem" :li
   "delete" :del
   "break" :br
   "inlineMath" :markdown/inline-math})

(defn node-to-tag
  [node]
  (or (node-tags (.-type node))
      (throw (js/Error. (str "Got unknown AST node: " (.-type node))))))

(defmulti transform (fn [node _] (.-type node)))

(defn transform-children [this defs]
  (map #(transform % defs) (.-children this)))

(defmethod transform "text" [this _]
  (entities/decode (.-value this)))

(defmethod transform "heading" [this defs]
  (make-hiccup-node :markdown/heading
                    {:level (.-depth this)}
                    (transform-children this defs)))

(defmethod transform "list" [this defs]
  (make-hiccup-node (if (.-ordered this) :ol :ul)
                    (transform-children this defs)))

(defmethod transform "code" [this _]
  ;FIXME needs to disambiguate between sources
  [:markdown/fenced-code-block
   {:language (.-lang this)}
   (.-value this)])

(defmethod transform "inlineCode" [this _]
  [:code {} (.-value this)])

(defmethod transform "link" [this defs]
  (make-hiccup-node :a
                    {:href (.-url this)
                     :title (.-title this)}
                    (transform-children this defs)))

(defmethod transform "table" [this defs]
  (let [alignment (.-align this)]
    (make-hiccup-node
     :table
     (for [[i row] (map-indexed vector (.-children this))]
       (make-hiccup-node
        :tr
        (for [[j cell] (map-indexed vector (.-children row))]
          (make-hiccup-node :markdown/table-cell
                            {:header? (= i 0)
                             :alignment (get alignment j)}
                            (transform-children cell defs))))))))

(defmethod transform "linkReference" [this defs]
  (make-hiccup-node :markdown/link-ref
                    {:reference (defs (.-identifier this))}
                    (transform-children this defs)))

(defmethod transform "definition" [this _]
  [:markdown/reference {:title (.-title this)
                        :label (.-identifier this)
                        :href (.-url this)}])

(defmethod transform "image" [this _]
  [:img {:src (.-url this)
         :alt (.-alt this)
         :title (.-title this)}])

(defmethod transform "html" [this _]
  (let [body (.-value this)]
    (if-let [[_ comment] (re-matches html-comment-re body)]
      [:markdown/html-comment {} comment]
      [:markdown/html {} body])))

(defmethod transform "footnoteReference" [this _]
  [:markdown/footnote {:id (.-identifier this)}])

(defmethod transform "footnoteDefinition" [this defs]
  ;FIXME to match behavior of flexmark
  [:markdown/footnote-block {:id (.-identifier this)
                             :content (make-hiccup-node :div (transform-children this defs))}])

(defmethod transform :default [this defs]
  (make-hiccup-node (node-to-tag this)
                    (transform-children this defs)))

(defn collect-definitions [node]
  (if (= "definition" (.-type node))
    (let [def (transform node)]
      {(:label (second def)) def})
    (into {} (for [child (.-children node)]
               (collect-definitions child)))))

(defn to-hiccup [ast]
  (transform ast (collect-definitions ast)))
