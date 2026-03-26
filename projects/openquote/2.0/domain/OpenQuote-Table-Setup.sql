  create table if not exists ##dbname##.core_configure_Component (
      systemId int primary key,
      name varchar(255)
  ) ENGINE=InnoDB;
  
  create table if not exists ##dbname##.core_configure_Parameter (
      systemId int primary key,
      parentSystemId int,
      rank int,
      value varchar(255)
  ) ENGINE=InnoDB;
  
  create table if not exists ##dbname##.core_configure_Group (
      systemId int primary key,
      parentSystemId int,
      rank int
  ) ENGINE=InnoDB;
        
  create table if not exists ##dbname##.commercial_party (
      systemId int primary key,
      addressId int,
      parentSystemId int,
      rank int
  ) ENGINE=InnoDB;
  
  create table if not exists ##dbname##.commercial_person (
      systemId int primary key,
      firstname varchar(80),
      surname varchar(80)
  ) ENGINE=InnoDB;
  
  create table if not exists ##dbname##.commercial_organisation (
      systemId int primary key,
      name varchar(80)
  ) ENGINE=InnoDB;
  
  create table if not exists ##dbname##.commercial_company (
      systemId int primary key,
      companyNumber varchar(20)
  ) ENGINE=InnoDB;
  
  create table if not exists ##dbname##.commercial_addressbook (
      systemId int primary key,
      mePartyType char(3),
      mePartyId int
  ) ENGINE=InnoDB;
  
  create table if not exists ##dbname##.commercial_address (
      systemId int primary key,
      line1 varchar(20),
      line2 varchar(20),
      line3 varchar(20),
      line4 varchar(20),
      line5 varchar(20),
      postcode varchar(10)
  ) ENGINE=InnoDB;
  
  create table if not exists ##dbname##.core_Type (
      systemId int auto_increment primary key,
      versionId int,
      type varchar(20)
  ) ENGINE=InnoDB;
  
  create table if not exists ##dbname##.core_Attribute (
      systemId int primary key,
      parentSystemId int,
      id varchar(255),
      value varchar(255),
      format varchar(255),
      unit varchar(255),
      rank int
  ) ENGINE=InnoDB;
  
  create table if not exists ##dbname##.insurance_Policy (
      systemId int primary key,
      id varchar(20),
      productTypeId varchar(80),
      policyNumber varchar(40),
      quotationNumber varchar(40),
      inceptionDate datetime,
      expiryDate datetime,
      status varchar(20),
      policyHolderPartyId long,
      policyHolderPartyType char(15),
      assessmentSheetId long
  ) ENGINE=InnoDB;
  
  create table if not exists ##dbname##.party_Party (
      systemId int primary key,
      type varchar(20),
      legalName varchar(80),
      addressId long
  ) ENGINE=InnoDB;
  
  create table if not exists ##dbname##.party_Person (
      systemId int primary key,
      title varchar(20),
      otherTitle varchar(20),
      firstname varchar(80),
      surname varchar(80),
      dateOfBirth date
  ) ENGINE=InnoDB;
  
  create table if not exists ##dbname##.party_Address (
      systemId int primary key,
      line1 varchar(80),
      line2 varchar(80),
      line3 varchar(80),
      line4 varchar(80),
      line5 varchar(80),
      town varchar(80),
      county varchar(80),
      country varchar(80),
      postcode varchar(80)
  ) ENGINE=InnoDB;
  
  create table if not exists ##dbname##.insurance_Asset (
      systemId int primary key,
      parentSystemId int,
      rank int,
      id varchar(20),
      assetTypeId varchar(80)
  ) ENGINE=InnoDB;
  
  create table if not exists ##dbname##.insurance_Section (
      systemId int primary key,
      parentSystemId int,
      rank int,
      id varchar(20),
      sectionTypeId varchar(80),
      included varchar(9),
      excluded varchar(9)
  ) ENGINE=InnoDB;
  
  create table if not exists ##dbname##.insurance_Coverage (
      systemId int primary key,
      parentSystemId int,
      rank int,
      id varchar(20),
      description varchar(256),
      enabled bit,
      optional bit,
      limitAmount double,
      limitCurrency char(4),
      deductibleAmount double,
      deductibleCurrency char(4)
  ) ENGINE=InnoDB;
  
  create table if not exists ##dbname##.insurance_AssessmentSheet (
      systemId int primary key,
      lockingActor varchar(20)
  ) ENGINE=InnoDB;
  
  create table if not exists ##dbname##.insurance_AssessmentLine (
      systemId int auto_increment primary key,
      versionId int,
      parentSystemId int,
      rank int,
      type varchar(20),
      id varchar(20),
      reason varchar(255),
      disabled bit,
      priority int,
      origin varchar(20),
      referenceType varchar(20),
      referenceId varchar(20),
      behaviourId varchar(20),
      markerType varchar(20),
      createdDate datetime,
      expiryDate datetime,
      reminderDate datetime,
      isManuscript bit,
      subjectivityText varchar(255),
      contributesTo varchar(255),
      dependsOn varchar(255),
      amount double,
      currency char(4),
      behaviourType varchar(20),
      rate varchar(20)
  ) ENGINE=InnoDB;
  
  create table if not exists ##dbname##.saved_policy_summary (
      id bigint(20) auto_increment primary key,
      version bigint(20),
      quote_number varchar(255),
      policy_number varchar(255),
      quote_date date,
      expiry_date date,
      status varchar(255),
      page varchar(255),
      premium_amount decimal(19,2),
      premium_currency varchar(255),
      username varchar(255),
      product varchar(255),
      user_saved bit,
      test_case bit
  ) ENGINE=InnoDB;
  
  create table if not exists ##dbname##.saved_policy (
      id bigint(20) primary key,
      policy text,
      quotation_document longblob,
      invoice_document longblob,
      wording_document longblob,
      certificate_document longblob
  ) ENGINE=InnoDB;

CREATE INDEX idx_policy_status ON ##dbname##.insurance_Policy (status);
CREATE INDEX idx_policy_productTypeId ON ##dbname##.insurance_Policy (productTypeId);
CREATE INDEX idx_policy_policyNumber ON ##dbname##.insurance_Policy (policyNumber);
CREATE INDEX idx_policy_quotationNumber ON ##dbname##.insurance_Policy (quotationNumber);
CREATE INDEX idx_section_parentSystemId ON ##dbname##.insurance_Section (parentSystemId);
CREATE INDEX idx_asset_parentSystemId ON ##dbname##.insurance_Asset (parentSystemId);
CREATE INDEX idx_assessmentline_parentSystemId ON ##dbname##.insurance_AssessmentLine (parentSystemId);
CREATE INDEX idx_attribute_parentSystemId ON ##dbname##.core_Attribute (parentSystemId);
