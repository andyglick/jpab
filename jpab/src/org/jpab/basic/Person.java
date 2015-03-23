/*
 * JPA Performance Benchmark - http://www.jpab.org
 * Copyright © ObjectDB Software Ltd. All Rights Reserved. 
 *
 * This is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation; either version 2 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.
 */

package org.jpab.basic;

import java.util.*;
import javax.persistence.*;

import org.jpab.*;

/**
 * A simple entity class with no inheritance/collections/indexes.
 */
@Entity
@TableGenerator(name="basicSeq", allocationSize=1000)
public class Person implements TestEntity {
	
	// Fields:

	@Id @GeneratedValue(strategy=GenerationType.TABLE, generator="basicSeq")
    private Integer id;

	private String firstName;
	private String middleName;
	private String lastName;
	private String street;
	private String city;
	private String state;
	private String zip;
	private String country;
	private String phone;
	private String email;

	@Temporal(TemporalType.DATE)
	private Date birthDate;
	@Temporal(TemporalType.DATE)
	private Date joinDate;
	@Temporal(TemporalType.DATE)
	private Date lastLoginDate;

	@Basic private int loginCount;

	// Constructors:

    public Person() {
    	// used by JPA to load an entity object from the database
    }

    public Person(Test test) {
    	firstName = Randomizer.randomFirstName();
    	middleName = Randomizer.randomMiddleName();
    	lastName = Randomizer.randomLastName();
    	street = Randomizer.randomStreet();
    	city = Randomizer.randomCity();
    	state = Randomizer.randomState();
    	zip = Randomizer.randomZip();
    	country = Randomizer.randomCountry();
    	phone = Randomizer.randomPhone();
    	email = Randomizer.randomEmail();
    	Date[] dates = Randomizer.randomDates(3);
    	birthDate = dates[0];
    	joinDate =  dates[1];
    	lastLoginDate = dates[2]; 
    	loginCount = Randomizer.randomInt(1, 100);
    }

	// Methods:

    public void load() {
		assert firstName != null && middleName != null && lastName != null &&
			street != null && city != null && state != null &&
			zip != null && country != null && phone != null && email != null &&
			birthDate != null && joinDate != null &&
			lastLoginDate != null && loginCount > 0;
    }

    public void update() {
    	lastLoginDate = new Date();
    	loginCount++;
    }

    @Override
    public String toString() {
    	StringBuilder sb = new StringBuilder(64);
    	sb.append(firstName);
    	if (middleName != null) {
        	sb.append(' ').append(middleName);
    	}
    	sb.append(' ').append(lastName);
        return sb.toString();
    }
}
