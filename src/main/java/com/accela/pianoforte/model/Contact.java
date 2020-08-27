package com.accela.pianoforte.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class Contact {
    private String company;
    private String street1;
    private String street2;
    private String city;
    private String state;
    private String postCode;
    private String telephone;
    private String email;
}
