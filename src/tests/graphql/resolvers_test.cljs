(ns tests.graphql.resolvers-test
  #_(:require [cljs.core.async :refer [go <!]]
            [cljs.nodejs :as nodejs]
            [cljs.test :refer-macros [deftest is testing async use-fixtures]]
            [district.server.async-db :as db]
            [district.server.logging]
            [district.shared.async-helpers :as async-helpers :refer [promise-> safe-go <?]]
            [ethlance.server.db :as ethlance-db]
            [ethlance.server.graphql.generator :as generator]
            [ethlance.server.graphql.server]
            [ethlance.server.graphql.utils :refer [run-query]]
            [mount.core :as mount]
            [taoensso.timbre :as log]
            [clojure.string :as str]))

#_(async-helpers/extend-promises-as-channels!)

;; Contains {"userAddress": "0x4c3f13898913f15f12f902d6480178484063a6fb"} signed with secret-token
(def access-token "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyQWRkcmVzcyI6IjB4NGMzZjEzODk4OTEzZjE1ZjEyZjkwMmQ2NDgwMTc4NDg0MDYzYTZmYiIsImlhdCI6MTU4ODA5NTQxNn0.qGvidhMxes5rjXWvQf32n1vNSepGr3F3-voItByYBpU")
(def secret-token "SECRET")

#_(use-fixtures :once
  {:before (fn []
             (async done
                    (safe-go
                     (log/debug "Running before fixture")
                     (let [comps #_<?
                           (-> (mount/with-args {:config {:default {:graphql {:sign-in-secret secret-token}}}
                                                 :district/db {:user "user"
                                                               :host "localhost"
                                                               :database "ethlance"
                                                               :password "pass"
                                                               :port 5432}
                                                 :ethlance/db {:resync? true}
                                                 :graphql {:port 4000
                                                           :sign-in-secret secret-token}
                                                 :logging {:level :debug
                                                           :console? true}})
                               (mount/only [#'district.server.logging/logging
                                            #'district.server.async-db/db
                                            #'district.server.config/config
                                            #'ethlance.server.db/ethlance-db
                                            #'ethlance.server.graphql.server/graphql])
                               (mount/start))]
                       (log/info "Started" comps))
                     (done))))
   :after (fn [] (log/debug "Running after fixture"))})

#_(deftest test-resolvers
  (async done
         (go
           (let [api-endpoint "http://localhost:4000/graphql"
                 _ (<! (generator/generate-dev-data))
                 {{{:keys [:user/address]} :user} :data
                  :as user-query} (<! (run-query {:url api-endpoint
                                                  :access-token access-token
                                                  :query [:user {:user/address "EMPLOYER"}
                                                          [:user/address]]}))

                 user-search-query (<! (run-query {:url api-endpoint
                                                   :access-token access-token
                                                   :query [:user-search {:offset 0 :limit 3}
                                                           [:total-count
                                                            [:items [:user/address]]]]}))
                 candidate-query (<! (run-query {:url api-endpoint
                                                 :access-token access-token
                                                 :query [:candidate {:user/address "CANDIDATE"}
                                                         [:user/address
                                                          [:candidate/feedback [:total-count
                                                                                [:items
                                                                                 [:job/id
                                                                                  :job-story/id
                                                                                  :feedback/to-user-type
                                                                                  :feedback/from-user-type
                                                                                  :feedback/rating]]]]]]}))

                 candidate-search-query-or (<! (run-query {:url api-endpoint
                                                           :access-token access-token
                                                           :query [:candidate-search {:skills-or ["Clojure" "Travis"]}
                                                                   [:total-count
                                                                    [:items [:user/address
                                                                             :candidate/skills]]]]}))

                 candidate-search-query-and (<! (run-query {:url api-endpoint
                                                            :access-token access-token
                                                            :query [:candidate-search {:skills-and ["Clojure" "Java"]}
                                                                    [:total-count
                                                                     [:items [:user/address
                                                                              :candidate/skills]]]]}))

                 employer-query (<! (run-query {:url api-endpoint
                                                :access-token access-token
                                                :query [:employer {:user/address "EMPLOYER"}
                                                        [:user/address
                                                         [:employer/feedback [:total-count
                                                                              [:items
                                                                               [:job/id
                                                                                :job-story/id
                                                                                :feedback/to-user-type
                                                                                :feedback/from-user-type
                                                                                :feedback/rating]]]]]]}))

                 arbiter-query (<! (run-query {:url api-endpoint
                                               :access-token access-token
                                               :query [:arbiter {:user/address "ARBITER"}
                                                       [:user/address
                                                        [:arbiter/feedback [:total-count
                                                                            [:items
                                                                             [:job/id
                                                                              :job-story/id
                                                                              :feedback/to-user-type
                                                                              :feedback/from-user-type
                                                                              :feedback/rating]]]]]]}))

                 job-id (-> candidate-query :data :candidate :candidate/feedback :items first :job/id)
                 job-query (<! (run-query {:url api-endpoint
                                           :access-token access-token
                                           :query [:job {:job/id job-id}
                                                   [:job/id
                                                    :job/accepted-arbiter-address
                                                    :job/employer-address
                                                    [:job/stories [:total-count
                                                                   [:items
                                                                    [:job/id
                                                                     :job-story/id
                                                                     [:job-story/employer-feedback [:job/id
                                                                                                    :job-story/id
                                                                                                    :feedback/rating
                                                                                                    :feedback/to-user-type
                                                                                                    :feedback/from-user-type]]
                                                                     [:job-story/candidate-feedback [:job/id
                                                                                                     :job-story/id
                                                                                                     :feedback/rating
                                                                                                     :feedback/to-user-type
                                                                                                     :feedback/from-user-type]]]]]]]]}))

                 job-story-query (<! (run-query {:url api-endpoint
                                                 :access-token access-token
                                                 :query [:job-story {:job-story/id 0 :job/id job-id}
                                                         [:job/id
                                                          :job-story/id
                                                          [:job-story/dispute [:total-count
                                                                               [:items
                                                                                [:job/id
                                                                                 :job-story/id
                                                                                 :dispute/reason]]]]
                                                          [:job-story/invoices [:total-count
                                                                                [:items
                                                                                 [:job/id
                                                                                  :job-story/id
                                                                                  :invoice/id
                                                                                  :invoice/amount-paid]]]]]]}))

                 dispute-query (<! (run-query {:url api-endpoint
                                               :access-token access-token
                                               :query [:dispute {:job-story/id 0 :job/id job-id}
                                                       [:job/id
                                                        :job-story/id
                                                        :dispute/reason]]}))

                 invoice-id (-> job-story-query :data :job-story :job-story/invoices :items first :invoice/id)
                 invoice-query (<! (run-query {:url api-endpoint
                                               :access-token access-token
                                               :query [:invoice {:job-story/id 0 :job/id job-id :invoice/id (or invoice-id 0)}
                                                       [:job/id
                                                        :job-story/id
                                                        :invoice/id
                                                        :invoice/amount-paid]]}))
                 ]

             (is (= "EMPLOYER" (-> user-query :data :user :user/address str/trim)))

             (is (= 3 (-> user-search-query :data :user-search :total-count)))
             (is (= 3 (-> user-search-query :data :user-search :items count)))

             (is (= "CANDIDATE" (-> candidate-query :data :candidate :user/address str/trim)))
             (is (= 5 (-> candidate-query :data :candidate :candidate/feedback :total-count)))
             (is (= 5 (-> candidate-query :data :candidate :candidate/feedback :items count)))
             (is (= "Employer" (-> candidate-query :data :candidate :candidate/feedback :items first :feedback/from-user-type)))
             (is (= "Candidate" (-> candidate-query :data :candidate :candidate/feedback :items first :feedback/to-user-type)))

             (is (= #{"Clojure" "Solidity"} (-> candidate-search-query-or :data :candidate-search :items first :candidate/skills set)))
             (is (= 0 (-> candidate-search-query-and :data :candidate-search :total-count)))

             (is (every? #(= "Employer" %) (-> employer-query :data :employer :employer/feedback :items (#(map :feedback/to-user-type %)) )))

             (is (every? #(= "Arbiter" %) (-> arbiter-query :data :employer :arbiter/feedback :items (#(map :feedback/to-user-type %)) )))

             (let [employer-feedbacks (-> employer-query :data :employer :employer/feedback :items)
                   employer-feedback (-> job-query :data :job :job/stories :items first :job-story/employer-feedback)
                   candidate-feedbacks (-> candidate-query :data :candidate :candidate/feedback :items)
                   candidate-feedback (-> job-query :data :job :job/stories :items first :job-story/candidate-feedback)]

               (is (= (-> (filter (fn [elem] (and (= job-id (:job/id elem))
                                                  (= (:job-story/id employer-feedback) (:job-story/id elem))))
                                  employer-feedbacks) first)
                      (-> job-query :data :job :job/stories :items first :job-story/employer-feedback)))

               (is (= (-> job-query :data :job :job/stories :items first :job-story/candidate-feedback)
                      (-> (filter (fn [elem] (and (= job-id (:job/id elem))
                                                  (= (:job-story/id candidate-feedback) (:job-story/id elem))))
                                  candidate-feedbacks) first))))

             (let [job-story-invoices (-> job-story-query :data :job-story :job-story/invoices :items)
                   job-story-dispute (-> job-story-query :data :job-story :job-story/dispute :items)]

               (is (= (-> invoice-query :data :invoice)
                      (first (filter (fn [elem] (and (= job-id (:job/id elem))
                                                     (= 0 (:job-story/id elem))
                                                     (= invoice-id (:invoice/id elem))))
                                     job-story-invoices))))

               (when-not (empty? job-story-dispute)
                 (is (= (-> dispute-query :data :dispute)
                        (first (filter (fn [elem] (and (= job-id (:job/id elem))
                                                       (= 0 (:job-story/id elem))))
                                       job-story-dispute))))))

             (done)))))

#_(deftest test-mutations
  (async done
         (go
           (let [api-endpoint "http://localhost:4000/graphql"
                 m1 (<! (run-query {:url api-endpoint
                                    :access-token access-token
                                    :type "mutation"
                                    :query [:send-message {:to "EMPLOYER" :text "Some message text"}]}))])
           (done))))
