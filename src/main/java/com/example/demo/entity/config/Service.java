package com.example.demo.entity.config;

import org.springframework.stereotype.Component;

@Component
public class Service {
    private String ustr = "";
    private Boolean skip = false;
    public Service(){
        this.ustr = ustr;
        this.skip = skip;
    }

    public String getUstr(){return ustr;}
    public void setUstr(String ustr){this.ustr = ustr;};
    public Boolean getSkip(){return skip;}
    public void setSkip(Boolean skip){this.skip = skip;}

}
