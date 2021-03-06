(ns yetibot.commands.karma
  (:require
   [yetibot.core.hooks :refer [cmd-hook]]
   [yetibot.models.karma :as model]
   [clojure.string :as str]
   [clj-time.format :as fmt]))

(defn- format-output
  [{{adapter :adapter} :chat-source} str]
  (let [formatter (condp = adapter
                    :slack #(str/replace % #"(@\w+)" "<$1>")
                    identity)]
    (formatter str)))

(defn get-score
  "karma <user> # get score and recent notes for <user>"
  {:yb/cat #{:fun}}
  [{user-id :match :as ctx}]
  (format-output
   ctx
   (str (format "%s: %s\n" user-id (model/get-score user-id))
        (str/join "\n" (map #(format "_\"%s\"_ --%s _(%s)_"
                                     (:note %)
                                     (:voter-id %)
                                     (fmt/unparse (fmt/formatters :mysql) (:created-at %)))
                            (model/get-notes user-id))))))

(defn get-high-scores
  "karma # get leaderboard"
  {:yb/cat #{:fun}}
  [ctx]
  (format-output
   ctx
   (str/join "\n" (map #(format "%s: %s" (:user-id %) (:score %))
                       (model/get-high-scores)))))

(defn adjust-score
  "karma <user>(++|--) <note> # adjust karma for <user> with optional <note>"
  {:yb/cat #{:fun}}
  [{[_ user-id action note] :match, {voter-id :id} :user}]
  (let [voter-id (str "@" voter-id)]
    (if (= action "++")
      (if (= user-id voter-id)
        ;; :thinking_face:
        "Sorry, that's not how Karma works. 🤔"
        (do
          (model/add-score-delta! user-id voter-id 1 note)
          ;; :purple_heart:
          "💜"))
      (do
        (model/add-score-delta! user-id voter-id -1 note)
        ;; :broken_heart:
        "💔"))))

(cmd-hook ["karma" #"^karma$"]
          #"(?x) (@?\w+) \s{0,2} (--|\+\+) (?: \s+(.+) )?" adjust-score
          #"@?\w+" get-score
          _ get-high-scores)
