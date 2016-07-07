package org.mobicents.restcomm.android.client.sdk;

import android.gov.nist.javax.sip.ResponseEventExt;
import android.javax.sip.Transaction;

import org.mobicents.restcomm.android.sipua.RCLogger;
import org.mobicents.restcomm.android.sipua.impl.SipManager;

import java.util.Arrays;
import java.util.HashMap;

public class JainSipJob {
    // Each transaction is associated with an FSM
    public class JainSipFsm {
        String[] states;
        //String id;
        JainSipJob.Type type;
        //HashMap<String, Object> parameters;
        JainSipClient jainSipClient;
        static final String TAG = "JainSipFsm";
        int index;

        JainSipFsm(JainSipJob.Type type, JainSipClient jainSipClient) {
            init(type, jainSipClient);
        }

        void init(JainSipJob.Type type, JainSipClient jainSipClient) {
            //this.id = id;
            this.type = type;
            this.jainSipClient = jainSipClient;
            //this.parameters = parameters;
            index = 0;

            if (type == JainSipJob.Type.TYPE_OPEN) {
                states = new String[]{"bind-register", "auth", "notify"};
            } else if (type == Type.TYPE_REGISTER_REFRESH) {
                states = new String[]{"register", "auth", "notify-error"};
            } else if (type == Type.TYPE_CLOSE) {
                states = new String[]{"unregister", "auth", "shutdown"};
            } else if (type == Type.TYPE_RECONFIGURE) {
                states = new String[]{"unregister", "auth", "register", "auth", "notify"};
            } else if (type == Type.TYPE_RECONFIGURE_RELOAD_NETWORKING) {
                states = new String[]{"unregister", "auth", "unbind-bind-register", "auth", "notify"};
            }
        }

        /*
        void init(String id, JainSipJob.Type type, JainSipClient jainSipClient) {
            this.init(id, type, jainSipClient);
        }
        */

        /* process FSM
         * id: the job id for the current job
         * event: the input event provided by the caller, to help FSM understand what kind of transition to do
         * arg: (optional) argument for the job
         * statusCode: (optional) the status code we want to convey to the UI thread typically
         * statusText: (optional) the status text we want to convey to the UI thread typically
         */
        void process(String id, String event, Object arg, RCClient.ErrorCodes statusCode, String statusText) {
            if (JainSipJob.this.id.equals(id)) {
                boolean loop;
                do {
                    loop = false;
                    if (index >= states.length) {
                        RCLogger.e(TAG, "process(): no more states to process");
                    }
                    if (type == Type.TYPE_OPEN) {
                        if (states[index].equals("bind-register")) {
                            try {
                                // TODO: fix static SipManager.NetworkInterfaceType.NetworkInterfaceTypeWifi and make dynamic based on current networking state
                                jainSipClient.jainSipBind(JainSipJob.this.id, parameters, JainSipClient.NetworkInterfaceType.NetworkInterfaceTypeWifi);

                                jainSipClient.jainSipStartStack(JainSipJob.this.id);

                                if (parameters.containsKey(RCDevice.ParameterKeys.SIGNALING_DOMAIN) && !parameters.get(RCDevice.ParameterKeys.SIGNALING_DOMAIN).equals("")) {
                                    // Domain has been provided do the registration
                                    transaction = jainSipClient.jainSipRegister(id, parameters);
                                } else {
                                    // No Domain there we are done here
                                    jainSipClient.listener.onClientOpenedEvent(id, RCClient.ErrorCodes.SUCCESS, RCClient.errorText(RCClient.ErrorCodes.SUCCESS));

                                    // TODO: we need to remove the current transaction since we are done with it
                                }
                            } catch (JainSipException e) {
                                jainSipClient.listener.onClientOpenedEvent(id, e.errorCode, e.errorText);
                            }
                        } else if (states[index].equals("auth")) {
                            // the auth step is optional hence we check if auth-required event was passed by the caller, if not we loop around to visit next state
                            if (event.equals("auth-required")) {
                                ResponseEventExt responseEventExt = (ResponseEventExt)arg;
                                try {
                                    jainSipClient.jainSipAuthenticate(id, JainSipJob.this, parameters, responseEventExt);
                                } catch (JainSipException e) {
                                    jainSipClient.listener.onClientOpenedEvent(id, e.errorCode, e.errorText);
                                }
                            } else {
                                loop = true;
                            }
                        } else if (states[index].equals("notify")) {
                            if (event.equals("register-success") || event.equals("register-failure")) {
                                jainSipClient.listener.onClientOpenedEvent(id, statusCode, statusText);
                            }
                        }
                    } else if (type == Type.TYPE_REGISTER_REFRESH) {
                        if (states[index].equals("register")) {
                            try {
                                // TODO: check if REGISTRATION_INITIAL is still needed
                                transaction = jainSipClient.jainSipRegister(id, parameters);
                            } catch (JainSipException e) {
                                jainSipClient.listener.onClientErrorEvent(id, e.errorCode, e.errorText);
                            }
                        } else if (states[index].equals("auth")) {
                            // the auth step is optional hence we check if auth-required event was passed by the caller, if not we loop around to visit next state
                            if (event.equals("auth-required")) {
                                ResponseEventExt responseEventExt = (ResponseEventExt)arg;
                                try {
                                    jainSipClient.jainSipAuthenticate(id, JainSipJob.this, parameters, responseEventExt);
                                } catch (JainSipException e) {
                                    jainSipClient.listener.onClientErrorEvent(id, e.errorCode, e.errorText);
                                }
                            } else {
                                loop = true;
                            }
                        } else if (states[index].equals("notify-error")) {
                            if (event.equals("register-failure")) {
                                jainSipClient.listener.onClientErrorEvent(id, statusCode, statusText);
                            }
                        }
                    } else if (type == Type.TYPE_CLOSE) {
                        if (states[index].equals("unregister")) {
                            try {
                                transaction = jainSipClient.jainSipUnregister(transactionId, parameters);

                            } catch (JainSipException e) {
                                jainSipClient.listener.onClientClosedEvent(id, e.errorCode, e.errorText);
                            }
                        } else if (states[index].equals("auth")) {
                            // the auth step is optional hence we check if auth-required event was passed by the caller, if not we loop around to visit next state
                            if (event.equals("auth-required")) {
                                ResponseEventExt responseEventExt = (ResponseEventExt)arg;
                                try {
                                    jainSipClient.jainSipAuthenticate(id, JainSipJob.this, parameters, responseEventExt);
                                } catch (JainSipException e) {
                                    jainSipClient.listener.onClientClosedEvent(id, e.errorCode, e.errorText);
                                }
                            } else {
                                loop = true;
                            }
                        } else if (states[index].equals("shutdown")) {
                            if (event.equals("register-failure")) {
                                RCLogger.w(TAG, "process(): unregister failed: " + Arrays.toString(Thread.currentThread().getStackTrace()));
                            } else if (event.equals("register-success") || event.equals("register-failure")) {
                                try {
                                    jainSipClient.jainSipUnbind();
                                    jainSipClient.jainSipStopStack();
                                    jainSipClient.listener.onClientClosedEvent(id, RCClient.ErrorCodes.SUCCESS, RCClient.errorText(RCClient.ErrorCodes.SUCCESS));
                                } catch (JainSipException e) {
                                    jainSipClient.listener.onClientClosedEvent(id, e.errorCode, e.errorText);
                                }
                            }
                        }
                    } else if (type == Type.TYPE_RECONFIGURE) {
                        if (states[index].equals("unregister")) {
                            try {
                                transaction = jainSipClient.jainSipUnregister(id, (HashMap<String, Object>) parameters.get("old-parameters"));
                            } catch (JainSipException e) {
                                RCLogger.w(TAG, "process(): unregister failed: " + Arrays.toString(Thread.currentThread().getStackTrace()));
                            }
                        } else if (states[index].equals("auth")) {
                            // the auth step is optional hence we check if auth-required event was passed by the caller, if not we loop around to visit next state
                            if (event.equals("auth-required")) {
                                ResponseEventExt responseEventExt = (ResponseEventExt)arg;
                                try {
                                    jainSipClient.jainSipAuthenticate(id, JainSipJob.this, (HashMap<String, Object>) parameters.get("old-parameters"), responseEventExt);
                                } catch (JainSipException e) {
                                    jainSipClient.listener.onClientReconfigureEvent(id, e.errorCode, e.errorText);
                                }
                            } else {
                                loop = true;
                            }
                        } else if (states[index].equals("register")) {
                            if (event.equals("register-failure")) {
                                // unregister step of reconfigure failed. Not that catastrophic, let's log it and continue; no need to notify UI thread just yet
                                RCLogger.e(TAG, "process(): unregister failed: " + Arrays.toString(Thread.currentThread().getStackTrace()));
                            }
                            if (event.equals("register-success") || event.equals("register-failure")) {
                                try {
                                    // update transactionId to reflect the newly picked one
                                    transactionId = Long.toString(System.currentTimeMillis());
                                    transaction = jainSipClient.jainSipRegister(transactionId, (HashMap<String, Object>) parameters.get("new-parameters"));
                                } catch (JainSipException e) {
                                    jainSipClient.listener.onClientReconfigureEvent(id, e.errorCode, e.errorText);
                                }
                            }
                        } else if (states[index].equals("auth")) {
                            // the auth step is optional hence we check if auth-required event was passed by the caller, if not we loop around to visit next state
                            if (event.equals("auth-required")) {
                                ResponseEventExt responseEventExt = (ResponseEventExt)arg;
                                try {
                                    jainSipClient.jainSipAuthenticate(id, JainSipJob.this, (HashMap<String, Object>) parameters.get("new-parameters"), responseEventExt);
                                } catch (JainSipException e) {
                                    jainSipClient.listener.onClientReconfigureEvent(id, e.errorCode, e.errorText);
                                }
                            } else {
                                loop = true;
                            }
                        } else if (states[index].equals("notify")) {
                            if (event.equals("register-success") || event.equals("register-failure")) {
                                jainSipClient.listener.onClientReconfigureEvent(id, statusCode, statusText);
                            }
                        }
                    } else if (type == Type.TYPE_RECONFIGURE_RELOAD_NETWORKING) {
                        if (states[index].equals("unregister")) {
                            try {
                                transaction = jainSipClient.jainSipUnregister(id, (HashMap<String, Object>) parameters.get("old-parameters"));
                            } catch (JainSipException e) {
                                RCLogger.w(TAG, "process(): unregister failed: " + Arrays.toString(Thread.currentThread().getStackTrace()));
                            }
                        } else if (states[index].equals("auth")) {
                            // the auth step is optional hence we check if auth-required event was passed by the caller, if not we loop around to visit next state
                            if (event.equals("auth-required")) {
                                ResponseEventExt responseEventExt = (ResponseEventExt)arg;
                                try {
                                    jainSipClient.jainSipAuthenticate(id, JainSipJob.this, (HashMap<String, Object>) parameters.get("old-parameters"), responseEventExt);
                                } catch (JainSipException e) {
                                    jainSipClient.listener.onClientReconfigureEvent(id, e.errorCode, e.errorText);
                                }
                            } else {
                                loop = true;
                            }
                        } else if (states[index].equals("unbind-bind-register")) {
                            if (event.equals("register-failure")) {
                                // unregister step of reconfigure failed. Not that catastrophic, let's log it and continue; no need to notify UI thread just yet
                                RCLogger.e(TAG, "process(): unregister failed: " + Arrays.toString(Thread.currentThread().getStackTrace()));
                            }
                            if (event.equals("register-success") || event.equals("register-failure")) {
                                try {
                                    jainSipClient.jainSipUnbind();

                                    //HashMap<String, Object> newParameters = (HashMap<String, Object>) parameters.get("new-parameters");

                                    // TODO: fix static SipManager.NetworkInterfaceType.NetworkInterfaceTypeWifi and make dynamic based on current networking state
                                    jainSipClient.jainSipBind(JainSipJob.this.id, (HashMap<String, Object>) parameters.get("new-parameters"), JainSipClient.NetworkInterfaceType.NetworkInterfaceTypeWifi);

                                    // update transactionId to reflect the newly picked one
                                    transactionId = Long.toString(System.currentTimeMillis());
                                    transaction = jainSipClient.jainSipRegister(transactionId, (HashMap<String, Object>) parameters.get("new-parameters"));
                                } catch (JainSipException e) {
                                    jainSipClient.listener.onClientReconfigureEvent(id, e.errorCode, e.errorText);
                                    jainSipJobManager.remove(id);
                                }
                            }
                        } else if (states[index].equals("auth")) {
                            // the auth step is optional hence we check if auth-required event was passed by the caller, if not we loop around to visit next state
                            if (event.equals("auth-required")) {
                                ResponseEventExt responseEventExt = (ResponseEventExt)arg;
                                try {
                                    jainSipClient.jainSipAuthenticate(id, JainSipJob.this, (HashMap<String, Object>) parameters.get("new-parameters"), responseEventExt);
                                } catch (JainSipException e) {
                                    jainSipClient.listener.onClientReconfigureEvent(id, e.errorCode, e.errorText);
                                }
                            } else {
                                loop = true;
                            }
                        } else if (states[index].equals("notify")) {
                            if (event.equals("register-success") || event.equals("register-failure")) {
                                jainSipClient.listener.onClientReconfigureEvent(id, statusCode, statusText);
                                jainSipJobManager.remove(id);
                            }
                        }
                    }
                    index++;
                } while (loop);
            }
        }
    }

    public enum Type {
        // TODO: remove those when we are done with new logic
        TYPE_REGISTRATION,
        TYPE_CALL,
        TYPE_MESSAGE,

        //
        TYPE_OPEN,
        TYPE_REGISTER_REFRESH,
        TYPE_CLOSE,
        TYPE_RECONFIGURE,
        TYPE_RECONFIGURE_RELOAD_NETWORKING,
        TYPE_RELOAD_NETWORKING,
    }

    /*
    public enum RegistrationType {
        REGISTRATION_INITIAL,
        REGISTRATION_REFRESH,
        REGISTRATION_UNREGISTER,
    }
    */

    // id is the App provided identification for this job and doesn't change until the job is finished
    public String id;
    // transactionId is the id for the current sip transaction. transactionId for the first transaction for this job is the same as the job id,
    // but as more sip transactions are created for the job, it is updated to reflect the latest sip transaction
    public String transactionId;
    public Type type;
    //public RegistrationType registrationType;
    public Transaction transaction;
    public HashMap<String, Object> parameters;
    //public Runnable onCompletion;
    public int authenticationAttempts;
    public static int MAX_AUTH_ATTEMPTS = 3;
    JainSipClient jainSipClient;
    JainSipJobManager jainSipJobManager;
    JainSipFsm jainSipFsm;
    static final String TAG = "JainSipJob";

    JainSipJob(JainSipJobManager jainSipJobManager, JainSipClient jainSipClient, String id, Type type, Transaction transaction, HashMap<String, Object> parameters) {
        this.id = id;
        this.transactionId = id;
        this.type = type;
        //this.registrationType = registrationType;
        this.transaction = transaction;
        this.parameters = parameters;
        this.authenticationAttempts = 0;
        this.jainSipClient = jainSipClient;
        //this.onCompletion = onCompletion;
        this.jainSipJobManager = jainSipJobManager;
        this.jainSipFsm = new JainSipFsm(type, jainSipClient);
    }

    /*
    JainSipJob(JainSipClient jainSipClient, String id, Type type, Transaction transaction, HashMap<String, Object> parameters) {
        this(jainSipClient, id, type, RegistrationType.REGISTRATION_INITIAL, transaction, parameters, null);
    }

    JainSipJob(JainSipClient jainSipClient, String id, Type type, RegistrationType registrationType, Transaction transaction, HashMap<String, Object> parameters) {
        this(jainSipClient, id, type, registrationType, transaction, parameters, null);
    }
    */

    void start() {

    }

    void processFsm(String id, String event, Object arg, RCClient.ErrorCodes statusCode, String statusText) {
        jainSipFsm.process(id, event, arg, statusCode, statusText);
    }

    void updateTransaction(Transaction transaction) {
        this.transaction = transaction;
    }

    void updateTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    // Should we retry authentication if previous failed? We retry a max of MAX_AUTH_ATTEMPTS
    boolean shouldRetry() {
        if (authenticationAttempts < JainSipJob.MAX_AUTH_ATTEMPTS - 1) {
            return true;
        } else {
            return false;
        }
    }

    void increaseAuthAttempts() {
        authenticationAttempts += 1;
    }
}