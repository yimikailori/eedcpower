(
(:create-pin
	("0" nil :quit)
	(:any (check-pin) :confirm-pin :create-pin nil))

(:confirm-pin
	("0" nil :quit)
	(:any (confirm-pin) :menu  :create-pin nil))

(:menu
	 ("1" nil :disco-menu-cash)
	 ("2" nil :disco-menu-credit)
	 ("0" nil :quit)
	 (:any nil :menu))

 (:disco-menu-cash
	 ("1" nil :vend-power-amt)
	 ("2" nil :vend-credit-amt)
	 ("0" nil :quit)
	 (:any nil :menu))

 (:disco-menu-credit
	 ("1" nil :vend-power-amt)
	 ("2" nil :vend-credit-amt)
	 ("0" nil :quit)
	 (:any nil :menu))

 (:vend-power-amt
         ("1"   (can-lend? 500) :meter-number  :vend-amt (session+ :amount-requested 500 :serviceq (fee :power 500) :amount-net (net :power 500) :loan-type :power :advance-name "N500"))
         ("2"   (can-lend? 1000) :meter-number  :vend-amt (session+ :amount-requested 1000 :serviceq (fee :power 1000) :amount-net (net :power 1000) :loan-type :power :advance-name "N1000"))
         ("3"   (can-lend? 1500) :meter-number  :vend-amt (session+ :amount-requested 1500 :serviceq (fee :power 1500) :amount-net (net :power 1500) :loan-type :power :advance-name "N1500"))
         ("4"   (can-lend? 2000) :meter-number  :vend-amt (session+ :amount-requested 2000 :serviceq (fee :power 2000) :amount-net (net :power 2000) :loan-type :power :advance-name "N2000"))
         ("5"   (can-lend? 2500) :meter-number  :vend-amt (session+ :amount-requested 2500 :serviceq (fee :power 2500) :amount-net (net :power 2500) :loan-type :power :advance-name "N2500"))
         ("6"   (can-lend? 3000) :meter-number  :vend-amt (session+ :amount-requested 3000 :serviceq (fee :power 3000) :amount-net (net :power 3000) :loan-type :power :advance-name "N3000"))
         ("0"  nil               :quit)
         ("#"  nil               :disco-menu-cash)
         (:any nil               :vend-power-amt))


(:vend-credit-amt
         ("1"   (can-lend? 500) :meter-number  :vend-amt (session+ :amount-requested 500 :serviceq (fee :power 500) :amount-net (net :power 500) :loan-type :power :advance-name "N500"))
         ("2"   (can-lend? 1000) :meter-number  :vend-amt (session+ :amount-requested 1000 :serviceq (fee :power 1000) :amount-net (net :power 1000) :loan-type :power :advance-name "N1000"))
         ("3"   (can-lend? 1500) :meter-number  :vend-amt (session+ :amount-requested 1500 :serviceq (fee :power 1500) :amount-net (net :power 1500) :loan-type :power :advance-name "N1500"))
         ("4"   (can-lend? 2000) :meter-number  :vend-amt (session+ :amount-requested 2000 :serviceq (fee :power 2000) :amount-net (net :power 2000) :loan-type :power :advance-name "N2000"))
         ("5"   (can-lend? 2500) :meter-number  :vend-amt (session+ :amount-requested 2500 :serviceq (fee :power 2500) :amount-net (net :power 2500) :loan-type :power :advance-name "N2500"))
         ("6"   (can-lend? 3000) :meter-number  :vend-amt (session+ :amount-requested 3000 :serviceq (fee :power 3000) :amount-net (net :power 3000) :loan-type :power :advance-name "N3000"))
         ("0"  nil               :quit)
         ("#"  nil               :disco-menu-credit)
         (:any nil               :vend-credit-amt))


(:credit-power-failed)


(:card-one
	("0" nil :quit)
    ("#" nil :menu)
	(:any (check-card-one) :card-two :card-one nil))

(:card-two
	("0" nil :quit)
    ("#" nil :menu)
	(:any (check-card-two) :card-three :card-two nil))

(:card-three
	("0" nil :quit)
    ("#" nil :menu)
	(:any (check-card-three) :credit-power :card-three nil))


(:meter-number
    	 ("0" nil :quit)
    	 ("#" nil :menu)
	   (:any (check-meter) :card-one :meter-number nil)))


(
(:create-pin
	(nil    (:en "Create your 4 digit Pin")))

(:confirm-pin
	(nil    (:en "Confirm your 4 digit Pin")))

 (:menu
        (nil     (:en "1. Buy Power with Cash"))
        (nil     (:en "2. Buy Power on Credit"))
        (nil     (:en "0. Cancel")))

 (:disco-menu-cash
        ((> max-qualified 0)     (:en "Choose DISCO"))
        ((> max-qualified 0)     (:en "1. EEDC"))
        ((> max-qualified 0)     (:en "2. Others"))
        ((> max-qualified 0)     (:en "0. Cancel"))
        (nil     (:en "#. Previous menu")))

 (:disco-menu-credit
         ((> max-qualified 0)     (:en "Choose DISCO"))
         ((> max-qualified 0)     (:en "1. EEDC"))
         ((> max-qualified 0)     (:en "2. Others"))
         ((> max-qualified 0)     (:en "0. Cancel"))
         (nil     (:en "#. Previous menu")))


 (:vend-power-amt
         ((and (> max-permissible 0))           (:en "Select amount, credit fee of 10% will be charged"))
         ((and (>= max-permissible 500))        (:en "1. N500"))
         ((and (>= max-permissible 1000))       (:en "2. N1000"))
         ((and (>= max-permissible 1500))       (:en "3. N1500"))
         ((and (>= max-permissible 2000))       (:en "4. N2000"))
         ((and (>= max-permissible 2500))       (:en "5. N2500"))
         ((and (>= max-permissible 3000))       (:en "6. N3000"))
         (nil                                   (:en  "0. Cancel"))
         (nil                                   (:en  "#. Back")))

 (:vend-credit-amt
          ((and (> max-permissible 0))           (:en "Select amount, credit fee of 10% will be charged"))
          ((and (>= max-permissible 500))        (:en "1. N500"))
          ((and (>= max-permissible 1000))       (:en "2. N1000"))
          ((and (>= max-permissible 1500))       (:en "3. N1500"))
          ((and (>= max-permissible 2000))       (:en "4. N2000"))
          ((and (>= max-permissible 2500))       (:en "5. N2500"))
          ((and (>= max-permissible 3000))       (:en "6. N3000"))
          (nil                                   (:en "0. Cancel"))
          (nil                                   (:en "#. Back")))




 (:credit-power
    (nil (:en ("Your EEDC power puchase of N":amount-net " is in process, you will receive email and text shortly"))))
 (:credit-power-failed
         (nil (:en "Your request for power advance was not successful. Please try again.")))



 (:meter-number
         (nil (:en "Enter the 11 digit meter number"))
         (nil (:en  "0. Back"))
         (nil (:en  "#. Menu")))

 (:card-one
         (nil (:en "Enter the 16 digit of your card"))
         (nil (:en  "0. Back"))
         (nil (:en  "#. Menu")))

 (:card-two
         (nil (:en "Enter the 3 digit cvv of your card"))
         (nil (:en  "0. Back"))
         (nil (:en  "#. Menu")))

 (:card-three
         (nil (:en "Enter the 4 digit PIN"))
         (nil (:en  "0. Back"))
         (nil (:en  "#. Menu")))

 (:menu-3p-confirm
         (nil (:en ("You are about to send a value of " :advance-name " to +234" :msisdn-3p ".")))
         (nil (:en "Press 1 to Proceed"))
         (nil (:en "Press any to go Back")))

 (:quit
         (nil (:en "Thank you for using the service.")))
  (:not-available
          (nil (:en "Option not available at the moment. Please try agai later"))))


(
(true () :menu))
