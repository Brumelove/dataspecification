package com.brume.dynamicdatamapper.usecases.serviceProviders;

import com.brume.dynamicdatamapper.domain.models.Attribute;
import com.brume.dynamicdatamapper.domain.models.Entry;
import com.brume.dynamicdatamapper.domain.models.Provider;
import com.brume.dynamicdatamapper.usecases.repositories.IAttributeRepository;
import com.brume.dynamicdatamapper.usecases.repositories.IEntryRepository;
import com.brume.dynamicdatamapper.usecases.repositories.IProviderRepository;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AppServiceProvider {

    private final IAttributeRepository attributeRepository;

    private final IProviderRepository providerRepository;

    private final IEntryRepository entryRepository;

    @Autowired
    public AppServiceProvider(IAttributeRepository attributeRepository, IProviderRepository providerRepository,
                              IEntryRepository entryRepository) {
        this.attributeRepository = attributeRepository;
        this.providerRepository = providerRepository;
        this.entryRepository = entryRepository;
    }

    public ExecutionMethodResponse createDataSpecification(Long providerId, List<String> fields) {
        if (fields.size () == 0) {
            return new ExecutionMethodResponse ( false, "fields definition can't be empty" );
        }

        val provider = new Provider ();
        provider.setId ( providerId );
        provider.setFields ( String.join ( ",", fields ) );

        providerRepository.save ( provider );

        return new ExecutionMethodResponse ( true, "successfully created provider" );

    }

    public ExecutionMethodResponse loadData(Long providerId, List<Map<String, Object>> data) {
        // so we'd first check if the provider exists.
        Optional<Provider> provider = providerRepository.findById ( providerId );
        if (provider.isPresent ()) {
            // next we validate the field specification.
            Provider gottenProvider = provider.get ();

            Set<String> fields = new HashSet<> ( Arrays.asList ( gottenProvider.getFields ().split ( "," ) ) );
            List<Entry> entriesToSave = new ArrayList<> ();
            for (int i = 0; i < data.size (); i++) {
                Entry entry = new Entry ();
                entry.setProvider ( gottenProvider );

                entryRepository.save ( entry );

                Map<String, Object> singleEntry = data.get ( i );
                log.info ( "Map data", singleEntry.values () );

                Set<String> listOfKeys = new HashSet<> ( singleEntry.keySet () );
                listOfKeys.removeAll ( fields );

                if (listOfKeys.size () > 0) {
                    return new ExecutionMethodResponse ( false, "Fields " + String.join ( ", ", listOfKeys )
                                                                + " weren't specified when creating the provider" );
                }

                List<Attribute> attributesList = new ArrayList<> ();

                for (Map.Entry<String, Object> attributeMap : singleEntry.entrySet ()) {
                    Attribute createdAttribute = Attribute.fromMapEntry ( entry, attributeMap );
                    attributesList.add ( createdAttribute );
                    attributeRepository.save ( createdAttribute );
                }

                entry.setAttributes ( attributesList );

                entriesToSave.add ( entry );
            }

            //gottenProvider.setEntries(entriesToSave);

            //providerRepository.save(gottenProvider);

            return new ExecutionMethodResponse ( true, "" );

        }
        return new ExecutionMethodResponse ( false, "Could not find provider" );
    }

    public List<Map<String, Object>> getDataForProvider(Long providerId, Map<String, String> filters) {
        Optional<Provider> provider = providerRepository.findById ( providerId );
        if (provider.isEmpty ()) {
            return null;
        }

        Provider gottenProvider = provider.get ();

        List<Map<String, Object>> results = new ArrayList<> ();
        Map<Long, Entry> entriesMap = new HashMap<> ();

        if (filters.size () > 0) {
            for (Map.Entry<String, String> filterEntry : filters.entrySet ()) {
                String attributeKey = filterEntry.getKey ();
                String filterValue = filterEntry.getValue ();

                List<String> filterSplit = Arrays.asList ( filterValue.split ( ":" ) );
                if (filterSplit.size () != 2) {
                    continue;
                }

                List<Attribute> eqcSearchResults = null;
                List<Attribute> eqSearchResults = null;
                List<Attribute> gtSearchResults = null;
                List<Attribute> ltSearchResults = null;

                if (filterSplit.get ( 0 ).equals ( "eqc" )) {
                    // this means we're comparing strings
                    eqcSearchResults = attributeRepository.findByProviderAndKeyAndValueContainingIgnoreCase (
                            gottenProvider, attributeKey, filterSplit.get ( 1 ) );
                }

                switch (filterSplit.get ( 0 )) {
                    case "eq":
                        eqSearchResults = attributeRepository.findByProviderAndKeyAndNumericValueEquals ( gottenProvider,
                                attributeKey, Integer.parseInt ( filterSplit.get ( 1 ) ) );
                        break;
                    case "gt":
                        gtSearchResults = attributeRepository.findByProviderAndKeyAndNumericValueGreaterThan (
                                gottenProvider, attributeKey, Integer.parseInt ( filterSplit.get ( 1 ) ) );
                        break;
                    case "lt":
                        ltSearchResults = attributeRepository.findByProviderAndKeyAndNumericValueLessThan (
                                gottenProvider, attributeKey, Integer.parseInt ( filterSplit.get ( 1 ) ) );
                        break;
                }

                Set<Attribute> intersectedSet = new HashSet<> ();

                if (eqcSearchResults != null) {
                    intersectedSet = new HashSet<> ( eqcSearchResults );
                }
                if (eqSearchResults != null) {
                    intersectedSet = intersectedSet.stream ().distinct ().filter ( eqSearchResults::contains )
                            .collect ( Collectors.toSet () );
                }

                if (gtSearchResults != null) {
                    intersectedSet = intersectedSet.stream ().distinct ().filter ( gtSearchResults::contains )
                            .collect ( Collectors.toSet () );
                }

                if (ltSearchResults != null) {
                    intersectedSet = intersectedSet.stream ().distinct ().filter ( ltSearchResults::contains )
                            .collect ( Collectors.toSet () );
                }

                List<Attribute> finalList = new ArrayList<> ( intersectedSet );
                for (Attribute attribute : finalList) {
                    Entry entry = attribute.getEntry ();
                    entriesMap.put ( entry.getId (), entry );
                }
            }

        }

        for (Map.Entry<Long, Entry> entryMapSet : entriesMap.entrySet ()) {
            results.add ( Entry.toMap ( entryMapSet.getValue () ) );
        }

        return results;
    }
}
