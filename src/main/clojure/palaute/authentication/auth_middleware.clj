(ns palaute.authentication.auth-middleware
  (:require
    [buddy.auth :refer [authenticated?]]
    [buddy.auth.middleware :refer [wrap-authentication]]
    [buddy.auth.accessrules :refer [wrap-access-rules success error]]
    [buddy.auth.backends.session :refer [session-backend]]
    [clojure.data.json :as json]
    [ring.util.request :refer [request-url]]
    [palaute.url-helper :refer [resolve-url]]
    [palaute.authentication.auth :refer [logged-in? superuser?]]))


(defn cas-auth-url
  []
  (resolve-url :cas.login))

(def backend (session-backend))

(defn any-access [request] true)

(defn- authenticated-access [request]
  (if (and (logged-in? request)
           (superuser? request))
    true
    (error "Authentication required")))

(defn- send-not-authenticated-api-response [& _]
  {:status  401
   :headers {"Content-Type" "application/json"}
   :body    (json/write-str {:error-message "Not authenticated"})})

(defn- redirect-to-login [request _]
  {:status  302
   :headers {"Location" (cas-auth-url)
             "Content-Type" "text/plain"}
   :session {:original-url (request-url request)}
   :body    (str "Access to " (:uri request) " is not authorized, redirecting to login")})

(def ^:private rules [{:pattern #".*/auth/.*"
                       :handler any-access}
                      {:pattern #".*/js/.*"
                       :handler any-access}
                      {:pattern #".*/images/.*"
                       :handler any-access}
                      {:pattern #".*/css/.*"
                       :handler any-access}
                      {:pattern #".*/favicon.ico"
                       :handler any-access}
                      {:pattern #".*/api/checkpermission"
                       :handler any-access}
                      {:pattern #".*/api/.*"
                       :handler authenticated-access
                       :on-error send-not-authenticated-api-response}
                      {:pattern #".*"
                       :handler authenticated-access
                       :on-error redirect-to-login}])

(defn with-authentication [site]
  (-> site
      (wrap-authentication backend)
      (wrap-access-rules {:rules rules})))
