package xyz.magicalstone.touchcontrol.skill.service;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.*;
import android.support.annotation.NonNull;
import org.json.JSONException;
import org.json.JSONObject;
import xyz.magicalstone.touchcontrol.skill.Skill;
import xyz.magicalstone.touchcontrol.skill.SkillRegistry;
import xyz.magicalstone.touchcontrol.skill.Util;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class ServiceSkillAdapter extends Skill {

    private final Context baseService;

    private final ComponentName providerServiceName;

    ServiceSkillAdapter(String id, String desc, Map<String, String> args, Context baseService, ComponentName providerServiceName) {
        super(id, desc, args);
        this.providerServiceName = providerServiceName;
        this.baseService = baseService;
    }

    @Override
    protected Map<String, String> active(Map<String, String> optimizedArgs) {
        Map<String, String> returnValue = new HashMap<>();
        final Exception[] exception = {null};
        Messenger receiver = new Messenger(new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                super.handleMessage(msg);
                Bundle data = msg.getData();
                if(!data.getBoolean("activated")) {
                    exception[0] = new IllegalArgumentException("The Skill this adapter " + id + " linked to doesn't exist.");
                    synchronized (returnValue) {
                        returnValue.notify();
                    }
                    return;
                }
                try {
                    returnValue.putAll(Util.jsonToStringMap(data.getString("result")));
                } catch (JSONException e) {
                    exception[0] = e;
                } finally {
                    synchronized (returnValue) {
                        returnValue.notify();
                    }
                }
            }
        });

        Intent intent = new Intent();
        intent.setComponent(providerServiceName);
        Message messageToSend = Message.obtain(null, ServiceMessageType.ACTIVE_SKILL.ordinal());
        Bundle bundle = new Bundle();
        bundle.putSerializable("skill", new SkillDataWrapper(id, "", optimizedArgs));
        messageToSend.setData(bundle);
        ServiceConnection connection = new OneTimeConnection(receiver, messageToSend);
        baseService.bindService(intent, connection, Context.BIND_AUTO_CREATE);

        try {
            synchronized (returnValue) {
                returnValue.wait();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        baseService.unbindService(connection);
        if (exception[0] != null) {
            returnValue.put("succeed", Boolean.toString(false));
            returnValue.put("reason", exception[0].getMessage());
        } else {
            returnValue.put("succeed", Boolean.toString(true));
        }
        return returnValue;
    }
}

